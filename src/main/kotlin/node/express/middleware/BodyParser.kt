package node.express.middleware

import node.express.RouteHandler

/**
 * Middleware that parses bodies of various types and assigned to the body attribute of the request
 */
fun bodyParser(): RouteHandler.()->Boolean {
  return chained(jsonBodyParser(), urlEncodedBodyParser())
}