package node.modules.netty

import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import node.express.Request
import node.express.Response
import java.io.OutputStream

class NettyResponse(req: Request, val ctx: ChannelHandlerContext) : Response(req) {
    private val msg = DefaultFullHttpResponse(HttpVersion.valueOf(req.version.toString()),
            HttpResponseStatus.OK)

    override fun sendHead() {
        msg.status = HttpResponseStatus.valueOf(status)
        headers.forEach { k, v -> msg.headers().set(k, v) }
        on("end"){
            ctx.writeAndFlush(msg).addListener {
                ctx.close()
                ChannelFutureListener.CLOSE.operationComplete(it as ChannelFuture)
            }
        }
    }

    override fun getRawOutput(): OutputStream {
        return object : OutputStream() {
            override fun write(b: Int) {
                msg.content().writeByte(b)
            }
        }
    }
}