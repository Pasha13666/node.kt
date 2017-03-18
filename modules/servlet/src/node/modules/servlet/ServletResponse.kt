package node.modules.servlet

import node.express.Request
import node.express.Response
import javax.servlet.http.HttpServletResponse

class ServletResponse(req: Request, private val rs: HttpServletResponse) : Response(req) {
    override fun sendHead() {
        rs.setStatus(status)
        headers.forEach { k, v -> rs.setHeader(k, v) }
        rs.flushBuffer()
    }

    override fun getRawOutput() = rs.outputStream!!
}