package node.modules.smtweb

import node.express.Express
import node.express.Request
import java.io.Reader

class SmtRequest(
        app: Express,
        _method: String,
        _uri: String,
        _version: String?,
        override val remoteIp: String,
        private val headers: Map<String, String>,
        override val rawBody: Reader
) : Request(app, _method, _uri, _version) {

    override fun header(key: String) = headers[key]
}