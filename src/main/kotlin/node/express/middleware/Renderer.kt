package node.express.middleware

import node.express.RouteHandler
import java.io.FileNotFoundException
import java.util.*

/**
 * Automatically processes templates relative to the location of the request
 */
fun renderer(): RouteHandler.()->Boolean {
  val files = HashMap<String, Boolean>()
  return {
    val requestPath = req.param("*") as? String ?: ""

    var path = requestPath
    if (!path.startsWith("/")) {
      path = "/" + path
    }
    if (path.endsWith("/")) {
      path = "${path}index"
    }

    if (!files.containsKey(requestPath)) {
      try {
        res.render(path)
        true
      } catch(e: FileNotFoundException) {
        files.put(requestPath, false)
        false
      } catch(e: IllegalArgumentException) {
        files.put(requestPath, false)
        false
      }
    } else false
  }
}