package node.express.session

import node.express.Cookie
import node.express.RouteHandler

/**
 * Defines a cookie parser
 */
fun cookieParser(): RouteHandler.() -> Boolean {
    return {
        val cookiesString = req.header("cookie")
        val cookieMap = hashMapOf<String, Cookie>()
        if (cookiesString != null)
            cookiesString.split(';', ',').map { Cookie.parse(it) }.forEach {
                cookieMap[it.key] = it
            }
        req.attributes["cookies"] = cookieMap
        false
    }
}