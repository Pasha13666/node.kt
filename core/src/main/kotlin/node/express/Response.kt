package node.express

import org.jboss.netty.channel.MessageEvent
import org.jboss.netty.handler.codec.http.DefaultHttpResponse
import org.jboss.netty.handler.codec.http.HttpVersion
import org.jboss.netty.handler.codec.http.HttpResponseStatus
import org.jboss.netty.channel.ChannelFutureListener
import org.jboss.netty.channel.ChannelFuture
import org.jboss.netty.handler.codec.http.HttpHeaders
import java.util.Date
import com.fasterxml.jackson.databind.JsonNode
import org.jboss.netty.buffer.ChannelBuffers
import org.jboss.netty.util.CharsetUtil
import node.EventEmitter
import java.io.File
import java.text.ParseException
import node.mimeType
import org.jboss.netty.handler.codec.http.HttpHeaders.Names
import org.jboss.netty.handler.stream.ChunkedFile
import org.jboss.netty.handler.stream.ChunkedStream
import java.io.ByteArrayInputStream
import node.http.asHttpFormatString
import node.http.asHttpDate
import java.util.HashMap
import node.util._with
import java.io.FileNotFoundException

/**
 * A response object
 */
class Response(req: Request, e: MessageEvent): EventEmitter() {
  val response = DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
  val e = e;
  val req = req;
  val locals = HashMap<String, Any>()

  val end = object : ChannelFutureListener {
    public override fun operationComplete(future: ChannelFuture?) {
      ChannelFutureListener.CLOSE.operationComplete(future);
      emit("end", this@Response);
    }
  };

  /**
   * Send a response code and an optional message. If message is not passed, a default message will be included if
   * it is available
   */
  fun send(code: Int, msg: String? = null) {
    status(code, msg);
    write();
  }

  /**
   * Redirect to the given url
   */
  fun redirect(url: String) {
    this.header("Location", url)
    status(302)
    write()
  }

  /**
   * Set the status code for this response. Does not send the response
   */
  fun status(code: Int, msg: String? = null): Response {
    if (msg != null) {
      response.setStatus(HttpResponseStatus(code, msg))
    } else {
      response.setStatus(HttpResponseStatus.valueOf(code))
    }
    return this
  }

  val status: Int
    get() {
      return response.getStatus()!!.getCode()
    }

  /**
   * Send a JSON response
   */
  fun send(json: JsonNode) {
    json(json)
  }

  fun send(b: ByteArray) {
    setIfEmpty(HttpHeaders.Names.CONTENT_LENGTH, b.size.toString());
    writeResponse();

    e.getChannel()!!.write(ChunkedStream(ByteArrayInputStream(b)))!!.addListener(end);
  }

  fun send(str: String) {
    setIfEmpty(HttpHeaders.Names.CONTENT_TYPE, "text/html");
    setResponseText(str);
    write();
  }

  fun contentType(t: String) {
    header(HttpHeaders.Names.CONTENT_TYPE, t);
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
    response.addHeader("Set-Cookie", cookie.toString())
  }


  fun header(key: String): String? {
    return response.getHeader(key);
  }

  fun get(key: String): String? {
    return header(key);
  }

  fun header(key: String, value: String): Response {
    response.setHeader(key, value);
    return this
  }

  fun set(key: String, value: String): Response {
    return header(key, value);
  }

  /**
   * Send JSON. Similar to send(), but the parameter can be any object,
   * which will be attempted to be converted to JSON.
   */
  fun json(j: Any) {
    val tree: JsonNode = json.valueToTree(j)!!
    json(tree);
  }

  /**
   * Send a JSON response.
   */
  fun json(j: JsonNode) {
    var callbackName: String? = null;
    if (req.app.enabled("jsonp callback")) {
      callbackName = req.query.get(req.app.get("jsonp callback name") as String);
    }
    var jsonText = j.toString();
    if (callbackName != null) {
      setIfEmpty(HttpHeaders.Names.CONTENT_TYPE, "application/javascript");
      setResponseText(callbackName + "(" + jsonText + ")")
    } else {
      setIfEmpty(HttpHeaders.Names.CONTENT_TYPE, "application/json");
      setResponseText(jsonText!!)
    }
    write();
  }

  private fun setResponseText(text: String) {
    var content = ChannelBuffers.copiedBuffer(text, CharsetUtil.UTF_8)!!
    response.setHeader(HttpHeaders.Names.CONTENT_LENGTH, content.writerIndex());
    response.setContent(content);
  }

  private fun write() {
    response.setHeader(HttpHeaders.Names.DATE, Date().asHttpFormatString());
    writeResponse().addListener(end);
  }

  private fun writeResponse(): ChannelFuture {
    emit("header", this)
    var channel = e.getChannel()!!
    return channel.write(response)!!
  }

  private fun setIfEmpty(key: String, value: Any) {
    if (response.getHeader(key) == null) {
      response.setHeader(key, value.toString());
    }
  }

  fun sendFile(file: File) {
    if (!file.exists()) return send(404);

    var size = file.length();
    var ifModifiedString = req.header(HttpHeaders.Names.IF_MODIFIED_SINCE);
    if (ifModifiedString != null) {
      try {
        var ifModifiedDate = ifModifiedString?.asHttpDate();
        if (ifModifiedDate!!.getTime() >= file.lastModified()) {
          return send(304);
        }
      } catch (e: ParseException) {
        // ignore
      }
    }

    setIfEmpty(HttpHeaders.Names.CONTENT_LENGTH, size);
    setIfEmpty(HttpHeaders.Names.DATE, Date().asHttpFormatString());
    setIfEmpty(HttpHeaders.Names.LAST_MODIFIED, Date(file.lastModified()).asHttpFormatString());

    setIfEmpty(HttpHeaders.Names.CONTENT_TYPE, file.mimeType() ?: "application/octet-stream");

    writeResponse();

    e.getChannel()!!.write(ChunkedFile(file))!!.addListener(end);
  }

  fun render(view: String, data: Map<String, Any?>? = null) {
    this.locals.put("request", req)
    var mergedContext = _with (HashMap<String, Any?>(locals)) {
      if (data != null) it.putAll(data)
    }
    send(req.app.render(view, mergedContext))
  }

  fun ok() {
    send(200)
  }
  fun internalServerError() {
    sendErrorResponse(500)
  }
  fun notImplemented() {
    sendErrorResponse(501)
  }
  fun badRequest() {
    sendErrorResponse(400)
  }
  fun forbidden() {
    sendErrorResponse(403)
  }
  fun notFound() {
    sendErrorResponse(404)
  }
  fun unacceptable() {
    sendErrorResponse(406)
  }
  fun conflict() {
    sendErrorResponse(409)
  }

  /**
   * Special handler for sending code responses when HTML is accepted. Looks for a template with the same name
   * as the code, then looks for a static page, then finally just detauls to an empty page
   */
  fun sendErrorResponse(code: Int) {
    if (req.accepts("html")) {
      try {
        render("errors/${code.toString()}")
      } catch(e: FileNotFoundException) {
        send(code);
      }
    } else {
      send(code);
    }
  }
}