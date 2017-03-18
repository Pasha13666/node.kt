package node.modules.lesscss

import node.express.RouteHandler
import org.lesscss.LessCompiler
import java.io.File

fun lessCompiler(basePath: String): RouteHandler.()->Boolean {
    fun normalizePath(path: String): String {
        var newPath = path
        if (newPath.startsWith("/")) {
            newPath = newPath.substring(1)
        }
        if (newPath.endsWith("/")) {
            newPath = newPath.substring(0, newPath.length - 1)
        }
        return newPath
    }

    val base = normalizePath(basePath)
    val cache = hashMapOf<String, File>()
    return l@{
        var path = req.param("*") as String
        var cssFile: File? = null

        if (path.endsWith(".less")) {
            if (!path.startsWith("/")) {
                path = "/" + path
            }

            val srcFile = java.io.File(base + path)

            if (!srcFile.exists()) {
                res.status = 404
                return@l res.end()
            } else {
                cssFile = cache[path]
                if (cssFile != null && cssFile.exists()) {
                    // check to see if the srcFile has been changed, and force recompile
                    if (cssFile.lastModified() < srcFile.lastModified()) {
                        cssFile = null
                    }
                }
                if (cssFile == null) {
                    cssFile = java.io.File.createTempFile("LessCss", ".css")
                    val lessCompiler = LessCompiler()
                    lessCompiler.compile(srcFile, cssFile)
                    cache.put(path, cssFile!!)
                }
            }
        }
        if (cssFile != null) {
            res.contentType("text/css")
            res.sendFile(cssFile)
            true
        } else false
    }
}