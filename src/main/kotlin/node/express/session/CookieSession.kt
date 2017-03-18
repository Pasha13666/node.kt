package node.express.session

import node.express.Cookie
import node.express.Request
import node.express.Response
import node.json.*
import node.util.log
import java.util.logging.Level


/**
 * A session where the entire contents of the session is stored in a cookie. Because of this,
 * clients need to be sure not to fill the session full of too much data (most browsers support
 * up to about 4K)
 */
class CookieSession(val req: Request, val res: Response, val cookieName: String) : Session {
    companion object : SessionFactory {
        var expirationTime: Long = 60 * 60 * 24
        var sessionKey: String = "_node_kt_session"
        override fun getSession(req: Request, res: Response): Session = CookieSession(req, res, sessionKey)
    }

    var jsonNode: MutableMap<String, Any?>? = null
    var hasChanged: Boolean = false

    init {
        val cookieContent = req.cookie(cookieName)
        if (cookieContent != null) {
            try {
                @Suppress("UNCHECKED_CAST")
                val cookieObject = cookieContent.asJson() as? Map<String, Any?>
                if (cookieObject != null) jsonNode = cookieObject.toMutableMap()
            } catch (t: Throwable) {
                log(Level.FINE, "Error parsing cookie: " + cookieContent, t)
            }
        }
    }

    override fun get(key: String): Any? {
        if (jsonNode == null) return null
        val result = jsonNode!![key]
        return result
    }

    override fun set(key: String, value: Any) {
        hasChanged = true
        if (jsonNode == null) {
            jsonNode = HashMap()
        }
        jsonNode!![key] = value
    }

    override fun save() {
        if (hasChanged) {
            if (jsonNode != null)
                res.cookie(Cookie(cookieName, jsonNode.toString()).maxAge(expirationTime))
            else res.cookie(Cookie(cookieName, "").maxAge(-1)) // clear cookie
        }
    }

    override fun clear() {
        hasChanged = true
        jsonNode = HashMap()
    }
}
