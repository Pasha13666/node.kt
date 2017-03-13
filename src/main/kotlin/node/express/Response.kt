package node.express

import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.*
import io.netty.handler.stream.ChunkedFile
import io.netty.handler.stream.ChunkedStream
import io.netty.util.CharsetUtil
import node.EventEmitter
import node.express.html.HTML
import node.http.asHttpDate
import node.http.asHttpFormatString
import node.mimeType
import node.util.io.pipe
import node.util.json.toJson
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.text.ParseException
import java.util.*

/**
 * Represents the response to an HTTP request
 */
class Response(val req: Request, val e: FullHttpRequest, val channel: ChannelHandlerContext) : EventEmitter() {
    val response = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
    val locals = HashMap<String, Any>()
    val status: Int
        get() = response.status!!.code()

    private val end = ChannelFutureListener {
        ChannelFutureListener.CLOSE.operationComplete(it)
        emit("end", this@Response)
    }

    /**
     * Send a response code and an optional message. If message is not passed, a default message will be included if
     * it is available
     */
    fun send(code: Int, msg: String? = null) {
        status(code, msg)
        write()
    }

    fun send(b: ByteArray) {
        setIfEmpty(HttpHeaders.Names.CONTENT_LENGTH, b.size.toString())
        writeResponse()

        channel.write(ChunkedStream(ByteArrayInputStream(b)))!!.addListener(end)
    }

    /**
     * Get the input stream
     */
    fun send(input: InputStream) {
        val content = response.content()
        input.pipe { bytes, i ->
            content.writeBytes(bytes, 0, i)
        }
        write()
    }

    /**
     * Send text as a response. If the content-type hasn't been previously sent, it will
     * be set to text/plain
     */
    fun send(str: String) {
        response.content().writeBytes(str.toByteArray(CharsetUtil.UTF_8))

        setIfEmpty(HttpHeaders.Names.CONTENT_TYPE, "text/plain; charset=UTF-8")
        response.headers().set(HttpHeaders.Names.CONTENT_LENGTH,
                response.content().readableBytes())

        write()
    }

    /**
     * Redirect to the given url. By default, uses a temporary redirect code (302), if
     * permanent is set to true, a 301 redirect code will be sent.
     */
    fun redirect(url: String, permanent: Boolean = false) {
        this.header("Location", url)
        status(if (permanent) 301 else 302)
        write()
    }

    /**
     * Set the status code for this response. Does not send the response
     */
    fun status(code: Int, msg: String? = null): Response {
        response.retain().status = if (msg != null) HttpResponseStatus(code, msg)
        else HttpResponseStatus.valueOf(code)
        return this
    }

    fun html(html: HTML) {
        contentType("text/html; charset=UTF-8")
        send(html.toString())
    }

    fun html(html: HTML.() -> Unit) {
        contentType("text/html; charset=UTF-8")
        send(HTML(init = html).toString())
    }

    fun contentType(t: String) {
        header(HttpHeaders.Names.CONTENT_TYPE, t)
    }

    /**
     * Add a new cookie
     */
    fun cookie(key: String, value: String) {
        cookie(Cookie(key, value))
    }

    /**
     * Add a new cookie
     */
    fun cookie(cookie: Cookie) {
        response.headers().add("Set-Cookie", cookie.toString())
    }


    fun header(key: String): String? {
        return response.headers().get(key)
    }

    fun get(key: String): String? {
        return header(key)
    }

    fun header(key: String, value: String): Response {
        response.headers().set(key, value)
        return this
    }

    fun set(key: String, value: String): Response {
        return header(key, value)
    }

    /**
     * Send JSON. Similar to send(), but the parameter can be any object,
     * which will be attempted to be converted to JSON.
     */
    fun json(j: Any?) {
        json(j.toJson())
    }

    /**
     * Send a JSON response.
     */
    fun json(j: String) {
        var callbackName: String? = null
        if (req.app.enabled("jsonp callback")) {
            callbackName = req.query.get(req.app.get("jsonp callback name") as String)
        }
        if (callbackName != null) {
            setIfEmpty(HttpHeaders.Names.CONTENT_TYPE, "application/javascript")
            setResponseText("$callbackName($j)")
        } else {
            setIfEmpty(HttpHeaders.Names.CONTENT_TYPE, "application/json")
            setResponseText(j)
        }
        write()
    }

    private fun setResponseText(text: String) {
        val bytes = text.toByteArray(CharsetUtil.UTF_8)
        response.content().retain().writeBytes(bytes)
        response.headers().set(HttpHeaders.Names.CONTENT_LENGTH, bytes.size)
    }

    private fun write() {
        response.headers().set(HttpHeaders.Names.DATE, Date().asHttpFormatString())
        writeResponse().addListener(end)
    }

    private fun writeResponse(): ChannelFuture {
        emit("header", this)
        return channel.writeAndFlush(response)!!
    }

    private fun setIfEmpty(key: String, value: Any) {
        if (response.headers().get(key) == null) {
            response.headers().set(key, value.toString())
        }
    }

    fun sendFile(file: File) {
        if (!file.exists()) return send(404)

        val size = file.length()
        val ifModifiedString = req.header(HttpHeaders.Names.IF_MODIFIED_SINCE)
        if (ifModifiedString != null) {
            try {
                val ifModifiedDate = ifModifiedString.asHttpDate()
                if (ifModifiedDate.time >= file.lastModified()) {
                    return send(304)
                }
            } catch (e: ParseException) {
                // ignore
            }
        }

        setIfEmpty(HttpHeaders.Names.CONTENT_LENGTH, size)
        setIfEmpty(HttpHeaders.Names.DATE, Date().asHttpFormatString())
        setIfEmpty(HttpHeaders.Names.LAST_MODIFIED, Date(file.lastModified()).asHttpFormatString())

        setIfEmpty(HttpHeaders.Names.CONTENT_TYPE, file.mimeType())

        writeResponse()

        channel.write(ChunkedFile(file)).addListener(end)
    }

    fun render(view: String, data: Map<String, Any?>? = null) {
        this.locals.put("request", req)
        val mergedContext = HashMap<String, Any?>(locals).apply {
            if (data != null) putAll(data)
        }
        send(req.app.render(view, mergedContext))
    }

    fun ok() = send(200)

    fun internalServerError() = sendErrorResponse(500)

    fun notImplemented() = sendErrorResponse(501)

    fun badRequest() = sendErrorResponse(400)

    fun forbidden() = sendErrorResponse(403)

    fun notFound() = sendErrorResponse(404)

    fun unacceptable() = sendErrorResponse(406)

    fun conflict() = sendErrorResponse(409)

    /**
     * Special handler for sending code responses when HTML is accepted. Looks for a template with the same name
     * as the code, then looks for a static page, then finally just detauls to an empty page
     */
    fun sendErrorResponse(code: Int) {
        if (req.accepts("html")) {
            try {
                render("errors/$code")
            } catch(e: Throwable) {
                send(code)
            }
        } else {
            send(code)
        }
    }
}