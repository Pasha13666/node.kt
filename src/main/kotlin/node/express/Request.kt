package node.express

import io.netty.channel.Channel
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpHeaders
import io.netty.handler.codec.http.QueryStringDecoder
import node.mimeType
import java.util.*

/**
 * The Http server request object
 */
class Request(val app: Express, val request: FullHttpRequest, val channel: Channel) {

  var params: Map<String, Any> = HashMap()
  var route: Route? = null
  var startTime = System.currentTimeMillis()
  var qsd = QueryStringDecoder(request.uri!!)
  val attributes: MutableMap<String, Any> = HashMap()
  val uri: String = request.uri!!
  var method: String = request.method!!.name()!!.toLowerCase()

  val path: String
    get() = qsd.path()!!

  val query = QueryString()

    /**
   * All of the cookies in a map
   */
  val cookies: Map<String, Cookie>
    get() = attributes["cookies"] as? Map<String, Cookie> ?: hashMapOf<String, Cookie>()

  inner class QueryString {
    fun get(key: String): String? {
      val p = qsd.parameters()
      if (p != null) {
        val v = p[key]
        if (v != null && v.size > 0) {
          return v[0]
        }
      }
      return null
    }
  }

  /**
   * Get a cookie value. Returns null if the cookie is not found
   */
  fun cookie(key: String): String? {
    return cookies[key]?.value
  }

  /**
   * Check if this request matches the given route. If so, set the parameters and route of this
   * request
   */
  fun checkRoute(route: Route, response: Response): Boolean {
    val params = route.match(this, response)
    if (params != null) {
      this.params = params
      this.route = route
      return true
    } else {
      return false
    }
  }

  /**
   * Get the value of a header
   */
  fun header(key: String) = request.headers().getAll(key).firstOrNull()

  /**
   * Check if this request is of a certain content type
   */
  fun isType(contentType: String) = header(HttpHeaders.Names.CONTENT_TYPE) == contentType

  /**
   * Get the remote ip address of the request
   */
  fun ip() = channel.remoteAddress().toString()

  class Accept(val mainType: String, val subType: String, val quality: Double) {
    /**
     * Match a given mime type
     */
    fun match(t: String): Boolean {
      val mime = if ('/' in t) t else t.mimeType()
      val parts = mime.split("/".toRegex()).toTypedArray()
      return (parts[0] == mainType || mainType == "*") && (parts[1] == subType || subType == "*")
    }
  }

  private fun parseAccept(): List<Accept> {
    val accept: String? = header(HttpHeaders.Names.ACCEPT) ?: return arrayListOf()
    val acceptArray = accept!!.split(",".toRegex()).toTypedArray()
    return acceptArray.map { it ->
      var result: Accept = Accept("", "", 1.0)
      val parts = it.split('/')
      if (parts.size == 2) {
        val quality = parts[1].split(";".toRegex()).toTypedArray()
        if (quality.size == 2) {
          val qVal = quality[1].split("=".toRegex()).toTypedArray()[1]
          result = Accept(parts[0], quality[0], qVal.toDouble())
        } else {
          result = Accept(parts[0], quality[0], 1.0)
        }
      }
      result
    }.sortedWith(Comparator<Accept> { o1, o2 ->
      if (o1.quality - o2.quality > 0) -1
      else if (o1.quality == o2.quality) 0
      else 1
    })
  }

  fun accepts(contentType: String): Boolean {
    return parseAccept().any { it.match(contentType) }
  }

  fun accepts(vararg contentTypes: String): String? {
    val accepts = parseAccept()
    for (a in accepts) {
      return contentTypes.firstOrNull { a.match(it) }?: continue
    }
    return null
  }

  /**
   * Use a map to have the renderer selected based on content type. Each
   * map entry is a map of a content type (or short form) to a function that
   * will be called if the content type is matched by the accept header
   */
  fun accepts(vararg options: Pair<String, ()->Unit>) {
    val accepts = parseAccept()
    for ((ct, cb) in options)
        if(accepts.firstOrNull { it.match(ct) } != null)
            return cb()
  }

  val body: Body?
    get() = attributes["body"] as? Body

  fun body(key: String) = body?.get(key)

  /**
   * Look up the value of a parameter, first by checking path parameters, then the contents of the body,
   * then query parameters
   */
  fun param(key: String): Any? {
    var p = params[key]
    if (p == null) {
      p = this.body(key)
      if (p == null) {
        p = query.get(key)
      }
    }
    return p
  }

  /**
   * Creates a data object from the parameters in the request.
   */
  fun <T> data(ty: Class<T>): T? {
      val obj: T = try {
          ty.newInstance()
      } catch (e: InstantiationException){
          return null
      } catch (e: IllegalAccessException){
          return null
      }

      ty.fields.filter { m -> m.name in params && ty.methods.find {
                  it.name == "set${m.name[0].toUpperCase()}${m.name.substring(1)}"
              } != null }.apply { requireParams(*map { it.name }.toTypedArray()) }.forEach {
          it.set(obj, params[it.name])
      }
      return obj
  }

  /**
   * Simple validation technique that simply checks that all of the parameters passed
   * are indeed provided
   */
  fun requireParams(vararg names: String) {
    throw ExpressException(400, "Missing parameter: ${names.firstOrNull { params[it] == null }?:return}")
  }

  /**
   * Check if this is a web socket request
   */
  fun isWebSocketRequest(): Boolean {
    val upgrade = request.headers().get("Upgrade")
    return (upgrade == "websocket")
  }
}

class MissingParameterException(vararg parameters: String) : IllegalArgumentException(parameters.joinToString())
fun MissingParameterException(parameters: List<String>): MissingParameterException {
  return MissingParameterException(*(parameters.toTypedArray()))
}