package node.express.middleware

import node.express.RouteHandler

/**
 * Adds the `X-Response-Time` header displaying the response
 * duration in milliseconds.
 */
fun responseTime(headerName: String = "X-Response-Time", format: String = "%dms"): RouteHandler.() -> Boolean {
    return {
        res.on("header") {
            res.headers[headerName] = java.lang.String.format(format, System.currentTimeMillis() - req.startTime)
        }
        false
    }
}