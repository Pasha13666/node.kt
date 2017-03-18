package node.express.session

import node.express.Request
import node.express.RouteHandler
import java.util.*

/**
 * Middleware to parse (and optionally validate) basic authentication credentials. Credentials are
 * saved to the request attributes as 'username' and 'password'.
 */
fun basicAuth(realm: String, validator: (String?, String?) -> Boolean): RouteHandler.() -> Boolean {
    return a@{
        try {
            val authHeaderValue = req.header("Authorization")
            if (authHeaderValue != null) {
                val (_, auth) = authHeaderValue.split(' ')
                val (username, password) = String(Base64.getDecoder().decode(auth)!!).split(':')
                if (validator(username, password)) {
                    req.attributes["user"] = username
                    return@a false
                }
            }
        } catch (t: Throwable) {
        }

        res.headers["WWW-Authenticate"] = "Basic realm=\"$realm\""
        res.status = 401
        res.end()
    }
}

/**
 * Parses token authorization and calls your validator to ensure that the requestor has permissions
 * to access the resource.
 */
fun tokenAuth(realm: String, validator: (String?, Request) -> Boolean): RouteHandler.() -> Boolean {
    return {
        var token: String? = null
        val authHeaderValue = req.header("Authorization")
        if (authHeaderValue != null) {
            val (type, tok) = authHeaderValue.split('=')
            if (type == "token")
                token = tok.trim('"')
        }
        if (validator(token, req))
            false
        else {
            res.headers["WWW-Authenticate"] = "Basic realm=\"$realm\""
            res.status = 401
            res.end()
        }
    }
}