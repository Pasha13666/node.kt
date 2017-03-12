package node.express.servers

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.*
import node.express.Express
import node.express.Request
import node.express.Response
import node.util.log
import java.net.InetSocketAddress

/**
 * Implementation of the express API using the Netty server engine
 */
class ExpressNetty: Express() {
    private val bootstrap: ServerBootstrap

    init {
        val bossGroup = NioEventLoopGroup()
        val workerGroup = NioEventLoopGroup()

        bootstrap = ServerBootstrap()
        bootstrap.group(bossGroup, workerGroup).
                channel(NioServerSocketChannel::class.java).
                childHandler(ServerInitializer())
    }

    /**
     * Initializes the request pipeline
     */
    private inner class ServerInitializer: ChannelInitializer<SocketChannel>() {
        override fun initChannel(ch: SocketChannel?) {
            val p = ch!!.pipeline()
            p.addLast("httpDecoder", HttpRequestDecoder())
            p.addLast("httpAggregator", HttpObjectAggregator(1048576))
            p.addLast("httpEncoder", HttpResponseEncoder() as ChannelHandler)
            p.addLast("handler", RequestHandler())
            p.addLast("exception", ExceptionHandler())
        }
    }

    /**
     * Our main Netty callback
     */
    private inner class RequestHandler : SimpleChannelInboundHandler<FullHttpRequest>() {
        override fun channelRead0(ctx: ChannelHandlerContext?, msg: FullHttpRequest?) {
            val req = Request(this@ExpressNetty, msg!!, ctx!!.channel()!!)
            val res = Response(req, msg, ctx)
            try {
                handleRequest(req, res, 0)
            } catch (t: Throwable) {
                errorHandler(t, req, res)
            }
        }
    }

    /**
     * A handler at the end of the chain that handles any exceptions that occurred
     * during processing.
     */
    private inner class ExceptionHandler: ChannelInboundHandlerAdapter() {
        override fun exceptionCaught(ctx: ChannelHandlerContext?, cause: Throwable?) {
            val response = DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                    HttpResponseStatus.INTERNAL_SERVER_ERROR)

            response.headers().set(HttpHeaders.Names.CONTENT_LENGTH, 0)
            ctx!!.writeAndFlush(response)
            ctx.close()
        }
    }

    /**
     * Start the server listening on the given port
     */
    fun listen(port: Int? = null) {
        var aPort = port
        if (aPort == null) {
            aPort = get("port") as Int
        }
        bootstrap.bind(InetSocketAddress(aPort))
        this.log("Express listening on port " + port)
    }
}