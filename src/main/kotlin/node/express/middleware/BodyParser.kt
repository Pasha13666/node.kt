package node.express.middleware

import node.express.Body
import node.express.RouteHandler
import node.json.*

/**
 * Middleware that parses bodies of various types and assigned to the body attribute of the request
 */
fun bodyParser(): RouteHandler.() -> Boolean {
    return chained(jsonBodyParser(), urlEncodedBodyParser())
}

/**
 * A request handler that can chain other handlers.
 */
fun chained(vararg handlers: RouteHandler.()->Boolean): RouteHandler.()->Boolean {
    return {
        val rh = RouteHandler(req, res)
        handlers.firstOrNull { it(rh) } != null
    }
}

/**
 * Parses the body of a request as a JSON object
 */
fun jsonBodyParser(): RouteHandler.() -> Boolean {
    return {
        if (req.body == null) {
            try {
                req.attributes["body"] = JsonBody(req.rawBody.readText().asJson())
            } catch(t: Throwable) {
            }
        }
        false
    }
}

/**
 * Body parser for URL encoded data
 */
fun urlEncodedBodyParser(): RouteHandler.()->Boolean {
    return {
        if (req.body == null) {
            try {
                req.attributes["body"] = UrlEncodedBody(req.rawBody.readText())
            } catch (e: Throwable) {
                // ignored
            }
        }
        false
    }
}

private class JsonBody(var node: Any?) : Body {
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

private class UrlEncodedBody(inp: String): Body {
    private val data = mapOf(*inp.split('&').map { it.split('=', limit = 1).let { it[0] to it[1] } }.toTypedArray())

    override val entries: Set<Map.Entry<String, Any?>>
        get() = data.entries

    override val keys: Set<String>
        get() = data.keys

    override val size: Int
        get() = data.size

    override val values: Collection<Any?>
        get() = data.values

    override fun containsKey(key: String) = key in data

    override fun get(key: String): Any? = data[key]

    override fun asInt(key: String) = data[key]?.toIntOrNull()

    override fun asString(key: String) = data[key]

    override fun asDouble(key: String) = data[key]?.toDoubleOrNull()

    override fun asInt(index: Int): Int? = throw UnsupportedOperationException()

    override fun asString(index: Int): String? = throw UnsupportedOperationException()

    override fun asNative() = data

    override fun isEmpty() = size == 0

    override fun containsValue(value: Any?) = value in values
}
