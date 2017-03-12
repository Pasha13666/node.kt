package node.express.middleware

import io.netty.util.CharsetUtil
import node.express.Body
import node.express.RouteHandler
import node.util.json.asJson

/**
 * Parses the body of a request as a JSON object
 */
fun jsonBodyParser(): RouteHandler.() -> Boolean {
  return {
    if (req.body == null) {
      try {
        val content = req.request.content()!!
        if (content.isReadable)
          req.attributes.put("body", JsonBody(content.toString(CharsetUtil.UTF_8).asJson()))
      } catch(t: Throwable) {
      }
    }
    false
  }
}

private class JsonBody(var node: Any?): Body {
    override fun get(key: String) = (node as Map<String, Any?>)[key]

    override fun asInt(key: String) = this[key] as? Int

    override fun asString(key: String) = this[key] as? String

    override fun asInt(index: Int) = (node as List<Any?>)[index] as? Int

    override fun asString(index: Int) = (node as List<Any?>)[index] as? String

    override fun asNative(): Any = node!!

    override fun asDouble(key: String) = this[key] as? Double

    override val size = (node as? Map<*, *>)?.size ?: (node as List<*>).size

    override fun isEmpty() = size == 0

    override fun containsKey(key: String) = key in (node as Map<*, *>)

    override fun containsValue(value: Any?) = (node as Map<*, *>).containsValue(value)

    override val keys: Set<String>
        get() = (node as Map<String, Any?>).keys

    override val values: Collection<Any?>
        get() = (node as Map<String, Any?>).values

    override val entries: Set<Map.Entry<String, Any?>>
        get() = (node as Map<String, Any?>).entries
}
