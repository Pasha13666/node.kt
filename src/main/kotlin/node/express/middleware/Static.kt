package node.express.middleware

import node.express.RouteHandler
import node.mimeType
import java.io.File
import java.util.*

/**
 * Middleware for serving a tree of static files
 */
fun static(basePath: String): RouteHandler.()->Boolean {
  val files = HashMap<String, File>() // a cache of paths to files to improve performance
  return {
    if (req.method != "get" && req.method != "head") false
    else {
      val requestPath = req.param("*") as? String ?: ""

      val srcFile: File? = files[requestPath] ?: {
        var path = requestPath
        if (!path.startsWith("/")) {
          path = "/" + path
        }
        if (path.endsWith("/")) {
          path += "index.html"
        }
        val f = File(basePath + path)
        if (f.exists()) {
          files.put(requestPath, f)
          f
        } else {
          null
        }
      }()

      if (srcFile != null) {
        res.sendFile(srcFile)
          true
      } else false
    }
  }
}

/**
 * Middleware that servers static resources from the code package
 */
fun staticResources(classBasePath: String): RouteHandler.()->Boolean {
    return {
        if (req.method != "get" && req.method != "head") false
        else {
            var requestPath = req.param("*") as? String ?: ""
            if (requestPath.isNotEmpty() && requestPath[0] == '/') {
                requestPath = requestPath.substring(1)
            }

            val resource = Thread.currentThread().contextClassLoader.getResource(classBasePath + requestPath)
            if (resource != null) {
                res.contentType(requestPath.mimeType())
                resource.openStream().use {
                    res.send(it)
                }
                true
            } else false
        }
    }
}