package node.modules.velocity

import node.express.Engine
import org.apache.velocity.VelocityContext
import org.apache.velocity.runtime.RuntimeConstants
import java.io.Writer

class VelocityEngine : Engine {
    val ve = org.apache.velocity.app.VelocityEngine()

    init {
        ve.setProperty(RuntimeConstants.RESOURCE_LOADER, "file")
        ve.setProperty(RuntimeConstants.FILE_RESOURCE_LOADER_PATH, "/")
        ve.setProperty(RuntimeConstants.FILE_RESOURCE_LOADER_CACHE, "true")
    }

    override fun render(path: String, data: Map<String, Any?>, out: Writer) {
        ve.getTemplate(path)!!.merge(VelocityContext(data), out)
    }
}