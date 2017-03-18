package node.express.middleware

import node.express.RouteHandler
import java.io.File

/**
 * FavIcon middleware, serves a site's Fav Icon
 */
fun favIcon(path: String, maxAge: Long = 86400000): RouteHandler.()->Boolean {
  val icon: ByteArray = File(path).readBytes()
  return {
    if (req.path == "/favicon.ico") {
      res.contentType("image/x-icon")
      res.headers["Content-Length"] = icon.size.toString()
      res.headers["Cache-Control"] = "public, max-age=" + (maxAge / 1000)
      res.sendOnly(icon)
    } else false
  }
}