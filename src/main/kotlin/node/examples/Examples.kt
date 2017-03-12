package node.examples

import node.express.html.Body
import node.express.html.html
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
    val express = ExpressNetty()
    express.get("/test"){
        res.html(html {
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
        })
        true
    }
    express.listen(3100)
}