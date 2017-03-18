package node.modules.servlet

import node.express.Express
import node.express.Request
import java.io.Reader
import javax.servlet.http.HttpServletRequest

class ServletRequest(app: Express, private val sr: HttpServletRequest) : Request(app, sr.method, sr.requestURI, null) {
    override val rawBody: Reader = sr.reader!!
    override val remoteIp = sr.remoteAddr!!
    override fun header(key: String): String? = sr.getHeader(key)
}