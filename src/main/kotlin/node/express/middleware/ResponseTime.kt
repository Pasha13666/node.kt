package node.express.middleware

import node.express.RouteHandler

/**
 * Adds the `X-Response-Time` header displaying the response
 * duration in milliseconds.
 */
fun responseTime(headerName: String = "X-Response-Time", format: String = "%dms"): RouteHandler.()->Boolean {
  return {
    val start = System.currentTimeMillis()
    res.on("header", {
      val time = System.currentTimeMillis() - start
      res.header(headerName, java.lang.String.format(format, time))
    })
    false
  }
}