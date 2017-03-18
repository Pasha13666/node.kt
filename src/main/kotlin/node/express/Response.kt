package node.express

import node.EventEmitter
import node.express.html.HTML
import node.mimeType
import node.util.asHttpDate
import node.util.asHttpFormatString
import node.util.extension
import node.json.*
import java.io.File
import java.io.OutputStream
import java.text.ParseException
import java.util.*

/**
 * Represents the response to an HTTP request
 */
abstract class Response(val req: Request) : EventEmitter() {
    val locals = HashMap<String, Any>()
    val headers = mutableMapOf<String, String>()
    var status: Int = 200
    private var headSent = false
    private var rawOut: OutputStream? = null

    private fun rawOutput(): OutputStream {
        if (rawOut == null){
            synchronized(this){
                if (rawOut == null)
                    rawOut = getRawOutput()
            }
        }
        return rawOut!!
    }

    protected abstract fun sendHead()
    protected abstract fun getRawOutput(): OutputStream

    fun end(): Boolean {
        if (!headSent){
            setIfEmpty("Date", Date().asHttpFormatString())
            emit("header", this)
            sendHead()
            headSent = true
        }

        emit("end", this@Response)
        return true
    }

    fun raw(): OutputStream {
        if(!headSent) {
            setIfEmpty("Date", Date().asHttpFormatString())
            emit("header", this)
            sendHead()
            headSent = true
        }

        if (rawOut == null)
            rawOut = getRawOutput()

        return rawOutput()
    }

    fun send(b: ByteArray) {
        if (!headSent) {
            setIfEmpty("Content-Length", b.size.toString())
            emit("header", this)
            sendHead()
            headSent = true
        }

        rawOutput().write(b)
    }

    fun sendOnly(b: ByteArray): Boolean{
        setIfEmpty("Content-Length", b.size.toString())
        raw().write(b)
        return end()
    }

    fun sendOnly(b: String): Boolean {
        setIfEmpty("Content-Type", "text/plain; charset=UTF-8")
        return sendOnly(b.toByteArray(Charsets.UTF_8))
    }

    /**
     * Send text as a response. If the content-type hasn't been previously sent, it will
     * be set to text/plain
     */
    fun send(str: String) {
        setIfEmpty("Content-Type", "text/plain; charset=UTF-8")
        send(str.toByteArray(Charsets.UTF_8))
    }

    /**
     * Redirect to the given url. By default, uses a temporary redirect code (302), if
     * permanent is set to true, a 301 redirect code will be sent.
     */
    fun redirect(url: String, permanent: Boolean = false): Boolean {
        headers["Location"] = url
        status = if (permanent) 301 else 302
        return end()
    }

    fun html(html: HTML): Boolean {
        contentType("text/html; charset=UTF-8")
        return sendOnly(html.toString())
    }

    fun html(html: HTML.() -> Unit): Boolean {
        contentType("text/html; charset=UTF-8")
        return sendOnly(HTML(init = html).toString())
    }

    fun contentType(t: String) {
        headers["Content-Type"] = t
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
        headers["Set-Cookie"] = cookie.toString()
    }

    operator fun get(key: String): String? {
        return headers[key]
    }

    operator fun set(key: String, value: String) {
        headers[key] = value
    }

    /**
     * Send JSON. Similar to send(), but the parameter can be any object,
     * which will be attempted to be converted to JSON.
     */
    fun json(j: Any?) = json(j.toJson())

    /**
     * Send a JSON response.
     */
    fun json(j: String): Boolean {
        var callbackName: String? = null
        if (req.app.enabled("jsonp callback")) {
            callbackName = req.query[req.app["jsonp callback name"] as String]
        }
        return if (callbackName != null) {
            setIfEmpty("Content-Type", "application/javascript")
            sendOnly("$callbackName($j)")
        } else {
            setIfEmpty("Content-Type", "application/json")
            sendOnly(j)
        }
    }

    private fun setIfEmpty(key: String, value: Any) {
        if (key !in headers)
            headers[key] = value.toString()
    }

    /**
     * Send file contents.
     * WARNING: calls [end]\()!
     */
    fun sendFile(file: File) {
        if (!file.exists()){
            status = 404
            end()
            return
        }

        val size = file.length()
        val ifModifiedString = req.header("If-Modified-Since")
        if (ifModifiedString != null) {
            try {
                val ifModifiedDate = ifModifiedString.asHttpDate()
                if (ifModifiedDate.time >= file.lastModified()) {
                    status = 304
                    end()
                    return
                }
            } catch (e: ParseException) {
                // ignore
            }
        }

        setIfEmpty("Content-Length", size)
        setIfEmpty("Date", Date().asHttpFormatString())
        setIfEmpty("Last-Modified", Date(file.lastModified()).asHttpFormatString())

        setIfEmpty("Content-Type", file.mimeType())
        emit("header", this)

        file.inputStream().copyTo(raw())
        end()
    }

    fun render(name: String, data: Map<String, Any?>? = null, render: Boolean = false) {
        this.locals.put("request", req)
        val mergedContext = HashMap<String, Any?>(locals).apply {
            if (data != null) putAll(data)
            putAll(req.app.locals)
        }
        var ext = name.extension()
        var viewFileName = name
        if (ext == null) {
            ext = req.app["view engine"] as? String ?: throw IllegalArgumentException("No default view set for view without extension")
            viewFileName = name + "." + ext
        }

        val renderer = req.app.engines[ext] ?: throw IllegalArgumentException("No renderer for ext: " + ext)
        val viewsPath = req.app["views"] as String
        val viewFile = File(viewsPath + viewFileName)
        if (!viewFile.exists()) {
            if (!render)
                return sendErrorResponse(404)
            end()
        }
        val viewPath = viewFile.absolutePath

        renderer.render(viewPath, mergedContext, raw().bufferedWriter(Charsets.UTF_8))
        end()
    }

    fun ok(){
        status = 200
        end()
    }

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
        status = code
        if (req.accepts("html")) {
            try {
                render("errors/$code", render = true)
            } catch(e: Throwable) {
            }
        }
        end()
    }
}