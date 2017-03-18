package node.modules.smtweb

import node.express.Request
import node.express.Response
import node.util.getMessage
import java.io.OutputStream

class SmtResponse(req: Request, private val out: OutputStream) : Response(req) {
    override fun sendHead() {
        out.write("${req.version} $status ${getMessage(status)}\r\n${headers.map { (k, v) -> "$k: $v\r\n" }.joinToString("")}\r\n".toByteArray())
        on("end"){
            out.flush()
        }
    }

    override fun getRawOutput(): OutputStream = out
}