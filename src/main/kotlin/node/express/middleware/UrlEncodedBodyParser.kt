package node.express.middleware

import io.netty.handler.codec.http.multipart.Attribute
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder
import node.express.Body
import node.express.RouteHandler
import java.util.*

/**
 * Body parser for URL encoded data
 */
fun urlEncodedBodyParser(): RouteHandler.()->Boolean {
  return {
    if (req.body == null) {
      try {
        val decoder = HttpPostRequestDecoder(DefaultHttpDataFactory(false), req.request)
        val data = decoder.bodyHttpDatas!!
        if (data.size > 0) {
          req.attributes.put("body", UrlEncodedBody(decoder))
        }
      } catch (e: Throwable) {
        // ignored
      }
    }
    false
  }
}

private class UrlEncodedBody(val decoder: HttpPostRequestDecoder): Body {
  override val entries: Set<Map.Entry<String, Any?>>
    get() = HashSet(decoder.bodyHttpDatas!!.map { BodyEntry(it as Attribute) })
  override val keys: Set<String>
    get() = HashSet(decoder.bodyHttpDatas!!.map { it.name!! })
  override val size: Int
    get() = decoder.bodyHttpDatas!!.size
  override val values: Collection<Any?>
    get() = decoder.bodyHttpDatas!!.map { (it as Attribute).value }

  override fun containsKey(key: String): Boolean {
    return decoder.bodyHttpDatas!!.find { it.name == key } != null
  }

  private fun getAttribute(key: String): Attribute? {
    return decoder.getBodyHttpData(key) as? Attribute
  }
  override fun get(key: String): Any? {
    return getAttribute(key)?.string
  }
  override fun asInt(key: String): Int? {
    return getAttribute(key)?.string?.toInt()
  }
  override fun asString(key: String): String? {
    return getAttribute(key)?.string
  }
  override fun asDouble(key: String): Double? {
    return getAttribute(key)?.string?.toDouble()
  }
  override fun asInt(index: Int): Int? {
    throw UnsupportedOperationException()
  }
  override fun asString(index: Int): String? {
    throw UnsupportedOperationException()
  }
  override fun asNative(): Any {
    return decoder
  }
  override fun isEmpty(): Boolean {
    return size == 0
  }
  override fun containsValue(value: Any?): Boolean {
    throw UnsupportedOperationException()
  }
  private data class BodyEntry(val att: Attribute): Map.Entry<String, Any?> {
    override val key: String
      get() = att.name!!
    override val value: Any?
      get() = att.value

    override fun hashCode(): Int {
      return att.hashCode()
    }
    override fun equals(other: Any?): Boolean {
      return att == other
    }
  }
}