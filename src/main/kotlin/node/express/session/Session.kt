package node.express.session

import node.express.Request
import node.express.Response
import node.express.RouteHandler
import node.util.log
import java.util.logging.Level

@FunctionalInterface
interface SessionFactory {
    fun getSession(req: Request, res: Response): Session
}

// Interface for a session
interface Session {
  fun get(key: String): Any?
  fun set(key: String, value: Any)

  fun save()
  fun clear()
}


fun session(storage: SessionFactory): RouteHandler.() -> Boolean {
    return {
        req.attributes["session"] = storage.getSession(req, res)
        false
    }
}

/**
 * Value extensions to ease access to the session object
 */
val Request.session: Session
    get() {
        if (this.attributes["session"] == null) {
            log(Level.SEVERE, "No session was found. Be sure that session middleware is installed!")
        }
        return this.attributes["session"] as Session
    }

val Response.session: Session
    get() {
        if (this.req.attributes["session"] == null) {
            log(Level.SEVERE, "No session was found. Be sure that session middleware is installed!")
        }
        return this.req.attributes["session"] as Session
    }
