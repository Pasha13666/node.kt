package node.modules.servlet

import node.express.Express
import java.io.IOException
import java.lang.reflect.Modifier
import java.util.*
import javax.servlet.ServletException
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class ServletExpress private constructor() : Express() {
    companion object {
        val app = ServletExpress()
        init {
            val string_array = Array(1){ String::class.java }
            Class.forName(System.getProperty("node.servlet-main")!!).methods
                    .first { it.name == "main" && Arrays.equals(it.parameterTypes, string_array)
                    && Modifier.isStatic(it.modifiers) && Modifier.isPublic(it.modifiers) }
                    .invoke(null, arrayOf<String>())
        }
    }

    class Servlet : HttpServlet() {
        @Throws(ServletException::class, IOException::class)
        override fun service(req: HttpServletRequest, resp: HttpServletResponse) {
            val req1 = ServletRequest(app, req)
            val res = ServletResponse(req1, resp)
            app.handleRequest(req1, res)
        }


    }
    override fun stop() {

    }

    override fun listen(port: Int) {

    }
}