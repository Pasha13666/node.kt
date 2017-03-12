package node.express.middleware

import node.express.RouteHandler
import node.util._logger

/**
 * Logger middleware. Logs the method and path, as well as the length of time required to complete the request.
 */
fun logger(): RouteHandler.()->Boolean {
    return {
        res.on("end", {
            val time = System.currentTimeMillis() - req.startTime
            req._logger.info(req.method + " " + req.path + " " + res.status + " " + time + "ms")
        })
        false
    }
}