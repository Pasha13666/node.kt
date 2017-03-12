package node.express.middleware

import node.express.Cookie
import node.express.RouteHandler

/**
 * Defines a cookie parser
 */
fun cookieParser(): RouteHandler.()->Boolean {
  return {
    val cookiesString = req.header("cookie")
    val cookieMap = hashMapOf<String, Cookie>()
    if (cookiesString != null) {
      val pairs = cookiesString.split("[;,]".toRegex()).toTypedArray()
      pairs.forEach {
        val cookie = Cookie.parse(it)
        if (!cookieMap.containsKey(cookie.key)) {
          cookieMap.put(cookie.key, cookie)
        }
      }
    }
    req.attributes["cookies"] = cookieMap
    false
  }
}