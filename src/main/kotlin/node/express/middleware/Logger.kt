package node.express.middleware

import node.express.RouteHandler
import node.util.log
import java.util.logging.Level

/**
 * Logger middleware. Logs the method and path, as well as the length of time required to complete the request.
 */
fun logger(): RouteHandler.()->Boolean {
    return {
        res.on("end"){
            log(Level.INFO, "${req.method} ${req.path} ${res.status} ${System.currentTimeMillis() - req.startTime}ms")
        }
        false
    }
}