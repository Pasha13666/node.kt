package node.modules.freemarker

import freemarker.template.Configuration
import freemarker.template.DefaultObjectWrapper
import node.express.Engine
import java.io.File
import java.io.Writer

class FreemarkerEngine : Engine {
    val fm = Configuration()

    init {
        fm.objectWrapper = DefaultObjectWrapper()
        fm.setDirectoryForTemplateLoading(File("/"))
    }

    override fun render(path: String, data: Map<String, Any?>, out: Writer){
        fm.getTemplate(path)!!.process(data, out)
    }
}