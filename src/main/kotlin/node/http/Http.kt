package node.http

import node.NotFoundException
import node.util.json.asJson
import node.util.json.toJson
import node.util.log
import node.util.logDebug
import org.apache.http.HttpException
import org.apache.http.HttpResponse
import org.apache.http.NameValuePair
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.*
import org.apache.http.client.utils.URIBuilder
import org.apache.http.client.utils.URLEncodedUtils
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager
import org.apache.http.message.BasicNameValuePair
import org.apache.http.util.EntityUtils
import java.io.IOException
import java.io.InputStream
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.*
import java.util.logging.Level

/**
 * A simplified API for making HTTP requests of all kinds. The goal of the library is to support 98% of use
 * cases while maintaining an easy to use API. Complicated request scenarious are not supported - for those,
 * use a full-featured library like HttpClient.
 */

private val client = run {
  HttpClientBuilder.create().setConnectionManager(PoolingHttpClientConnectionManager()).build()
}

enum class HttpMethod {
  GET,
  POS,
  PUT,
  DELETE,
  OPTIONS,
  HEAD,
  TRACE
}

/**
 * An HTTP request.
 */
class Request(private val request: HttpRequestBase) {
  private var response: HttpResponse? = null
  private var formParameters: MutableList<NameValuePair>? = null // stores form parameters

  /**
   * Called when the response has a status code >= 400. Default implementation throws an exception
   * and consumed the entity
   */
  var errorHandler: (Request)->Unit = HttpClient.defaultErrorHandler

  // The URL associated with this request
  val url: String = request.uri.toString()

  /**
   * Get the value of a header. Returns null if the key doesn't exist in the response.
   */
  fun header(key: String): String? {
    connect()
    val headers = response?.getHeaders(key)
    return if (headers != null && headers.isNotEmpty()) headers[0]?.value else null
  }

  fun withErrorHandler(handler: (Request)->Unit): Request {
    this.errorHandler = handler
    return this
  }

  /**
   * Set a request header
   */
  fun header(key: String, value: String): Request {
    request.setHeader(key, value)
    return this
  }

  /**
   * Get the status code from the response
   */
  fun status(): Int {
    connect()
    return response!!.statusLine!!.statusCode
  }

  /**
   * Get the entire status line of the response
   */
  fun statusLine(): String {
    connect()
    return response!!.statusLine!!.toString()
  }

  /**
   * Set the content type for a request
   */
  fun contentType(contentType: String): Request {
    return header("Content-Type", contentType)
  }

  /**
   * Get the content type for a request
   */
  fun contentType(): String? {
    val h = header("Content-Type")
    return if (h != null) {
      val components = h.split(";".toRegex()).toTypedArray()
      if (components.isNotEmpty()) components[0] else null
    } else null
  }

  /**
   * Add an accept header to the request
   */
  fun accepts(contentType: String) {
    request.setHeader("Accept", contentType)
  }

  /**
 * Get the body of the response as a json string, setting the appropriate Accept header
 */
  fun jsonString(): String? {
    // if we haven't connected, and there's no 'Accept' header, set it to
    // json to get the server to send us json
    if (response == null && request.getFirstHeader("Accept") == null)
      request.setHeader("Accept", "application/json")
    connect()
    return text()
  }

  /**
   * Parse the body of the response as json, mapped to a generic (Map or Array) object.
   */
  fun json(): Any? {
    try {
      return jsonString()!!.asJson()
    } catch (e: Throwable) {
      throw IOException(e)
    }
  }

  /**
   * Set the body of the request to json. Sets the content type appropriately. This will only work with
   * request types that allow a body.
   */
  fun json(body: Any?): Request {
    contentType("application/json")
    val entity = StringEntity(body.toJson())
    (request as HttpEntityEnclosingRequestBase).entity = entity
    return this
  }

  /**
   * Retrieve a response as text.
   */
  fun text(): String? {
    connect()
    return EntityUtils.toString(response!!.entity!!)
  }

  /**
   * Retrieve the response of type application/x-www-form-urlencoded.
   */
  fun form(): Map<String, String> {
    if (request.getFirstHeader("Accept") == null) {
      accepts("application/x-www-form-urlencoded")
    }
    connect()
    val str = EntityUtils.toString(response!!.entity!!)
    val values = URLEncodedUtils.parse(str, Charset.forName("UTF-8"))!!
    val result = HashMap<String, String>()
    for (value in values) {
      result.put(value.name!!, value.value!!)
    }
    return result
  }

  /**
   * Get the response body as an input stream.
   */
  fun body(): InputStream {
    connect()
    return response!!.entity!!.content!!
  }

  /**
   * Add form parameters. Since this call returns the Request object itself, it's fairly easy to chain calls
   * Request.post("http://service.com/upload").form("name","Some Name").form("age", 38)
   *
   * @param key the key of the form parameter
   * @param value the value of the form parameter
   */
  fun form(key: String, value: Any): Request {
    formParameters = formParameters ?: ArrayList<NameValuePair>()
    formParameters!!.add(BasicNameValuePair(key, value.toString()))
    return this
  }

  /**
   * Add a query parameter to the request. Returns the request object to support chaining.
   */
  fun query(key: String, value: String): Request {
    val builder = URIBuilder(request.uri!!)
    builder.setParameter(key, value)
    request.uri = builder.build()
    return this
  }

  /**
   * Consume the body of the response, leaving the connection
   * ready for more activity
   */
  fun consume(): Request {
    EntityUtils.consume(response!!.entity)
    return this
  }

  fun connect(): Request {
    if (response == null) {
      if (formParameters != null) {
        val entity = (request as HttpEntityEnclosingRequestBase).entity
        if (entity != null) {
          throw HttpException("Multiple entities are not allowed. Perhaps you have set a body and form parameters?")
        }
        request.entity = UrlEncodedFormEntity(formParameters!!)
      }
      this.logDebug("Connecting to ${this.url}")
      response = client.execute(request)
      checkForResponseError()
    }
    return this
  }

  private fun checkForResponseError() {
    val statusCode = response!!.statusLine!!.statusCode

    this.log("Connection to ${this.url} resulted in ${response!!.statusLine}", Level.FINE)
    if (statusCode >= 400) {
      this.errorHandler(this)
    }
  }
}

object HttpClient {
  val defaultErrorHandler: (Request)->Unit = { request->
    request.consume()
    if (request.status() == 404) {
      throw NotFoundException(request.url)
    } else {
      throw IOException(request.statusLine())
    }
  }

  /**
   * Initiate a GET request
   */
  fun get(url: String): Request {
    return Request(HttpGet(url))
  }

  fun post(url: String): Request {
    return Request(HttpPost(url));
  }

  fun put(url: String): Request {
    return Request(HttpPut(url));
  }

  fun delete(url: String): Request {
    return Request(HttpDelete(url));
  }

  fun head(url: String): Request {
    return Request(HttpHead(url));
  }

  fun options(url: String): Request {
    return Request(HttpOptions(url));
  }
}

val httpFormat = "EEE, dd MMM yyyy HH:mm:ss zzz";

/**
 * Format a date as an Http standard string
 */
fun Date.asHttpFormatString(): String {
  val sd = SimpleDateFormat(httpFormat)
  sd.timeZone = TimeZone.getTimeZone("GMT")
  return sd.format(this)
}

/**
 * Parse an HTTP date string
 */
fun String.asHttpDate(): Date {
  val sd = SimpleDateFormat(httpFormat)
  sd.timeZone = TimeZone.getTimeZone("GMT")
  return sd.parse(this)!!
}
