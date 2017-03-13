package node.examples

import node.express.html.Body
import node.express.middleware.logger
import node.express.middleware.static
import node.express.servers.ExpressNetty

fun Body.cetHeader() {
    header(clas="cet-page-header") {
        div(clas="cet-page-header-content") {
            div(clas="cet-page-header-logo") {
                a(href="/linq") {
                    +"The Linq"
                }
            }
        }
    }
}

fun main(args: Array<String>) {
    val app = ExpressNetty()
    app.use(logger())
    app.get("/"){
        res.html {
            head {
                base("https://www.caesars.com")
                css("//maxcdn.bootstrapcdn.com/font-awesome/4.1.0/css/font-awesome.min.css")
                css("/etc/designs/caesars/foundation.min.cb31886de5456178d84abd0f8c5b091e.css")
                css("/content/cet-themes/default/linq.e618539793dc6cd332b058c1ae3225c9.css")
                meta("name" to "og:type", "content" to "website")
            }
            body {
                cetHeader()
            }
        }
        true
    }

    app.get("/static/*", static("./"))
    app.get("/api/:name/:value"){
        res.status(200)
        res.json(mapOf(
                "name" to req.param("name"),
                "value" to req.param("value")
        ))
        true
    }

    app.listen(3100)
}