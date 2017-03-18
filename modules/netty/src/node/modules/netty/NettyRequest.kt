package node.modules.netty

import io.netty.channel.Channel
import io.netty.handler.codec.http.FullHttpRequest
import node.express.Express
import node.express.Request
import java.io.InputStream

class NettyRequest(app: Express, val msg: FullHttpRequest, channel: Channel)
    : Request(app, msg.method.name()!!, msg.uri!!, msg.protocolVersion.text()!!) {
    override val rawBody = object : InputStream() {
        override fun read() = msg.content().let {
            if (it.readableBytes() > 1) it.readByte().toInt() else -1
        }
    }.reader(Charsets.UTF_8)
    override val remoteIp = channel.remoteAddress().toString()

    override fun header(key: String) = msg.headers()?.get(key)
}
