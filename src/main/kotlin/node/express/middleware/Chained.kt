package node.express.middleware

import node.express.RouteHandler

/**
 * A request handler that can chain other handlers.
 */
fun chained(vararg handlers: RouteHandler.()->Boolean): RouteHandler.()->Boolean {
    return v@{
        val rh = RouteHandler(req, res)
        for (i in handlers) if (i(rh)) return@v true
        false
    }
}