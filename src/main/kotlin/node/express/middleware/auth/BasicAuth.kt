package node.express.middleware.auth

import node.crypto.decode
import node.express.Request
import node.express.RouteHandler
import node.util.splitToMap

/**
 * Middleware to parse (and optionally validate) basic authentication credentials. Credentials are
 * saved to the request attributes as 'username' and 'password'.
 */
fun basicAuth(realm: String, validator: (String?, String?)->Boolean): RouteHandler.()->Boolean {
  return {
    var username: String? = null
    var password: String? = null
    try {
      val authHeaderValue = req.header("authorization")
      if (authHeaderValue != null) {
        var auth = authHeaderValue.splitToMap(" ", "type", "data")
        val authString = auth["data"]!!.decode("base64")

        auth = authString.splitToMap(":", "username", "password")
        username = auth["username"]
        password = auth["password"]
      }
    } catch (t: Throwable) {
    }

    if (validator(username, password)) {
      if (username != null) {
        req.attributes["user"] = username
      }
      false
    } else {
      res.header("WWW-Authenticate", "Basic realm=\"" + realm + "\"")
      res.send(401)
      true
    }
  }
}

/**
 * Parses token authorization and calls your validator to ensure that the requestor has permissions
 * to access the resource.
 */
fun tokenAuth(realm: String, validator: (String?,Request)->Boolean): RouteHandler.()->Boolean {
  return {
    var token: String? = null
    val authHeaderValue = req.header("authorization")
    if (authHeaderValue != null) {
      val auth = authHeaderValue.splitToMap("=", "type", "token")
      if (auth["type"] == "token") {
        token = auth["token"]!!.replace("^\"|\"$".toRegex(), "")
      }
    }
    if (validator(token, req))
      false
    else {
      res.header("WWW-Authenticate", "Basic realm=\"$realm\"")
      res.send(401)
      true
    }
  }
}