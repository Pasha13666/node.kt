package node.modules.netty

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.*
import io.netty.handler.ssl.SslHandler
import node.express.Express
import node.util.createSSLContext
import node.util.log
import java.net.InetSocketAddress
import javax.net.ssl.SSLContext
import kotlin.system.exitProcess

/**
 * Implementation of the express API using the Netty server engine
 */
class NettyExpress : Express() {
    override fun stop() {
        exitProcess(0)
    }

    private val bootstrap: ServerBootstrap

    init {
        val bossGroup = NioEventLoopGroup()
        val workerGroup = NioEventLoopGroup()

        bootstrap = ServerBootstrap()
        bootstrap.group(bossGroup, workerGroup).
                channel(NioServerSocketChannel::class.java).
                childHandler(ServerInitializer())
    }

    private inner class ServerInitializer : ChannelInitializer<SocketChannel>() {
        override fun initChannel(ch: SocketChannel?) {
            val p = ch!!.pipeline()
            p.addLast("httpDecoder", HttpRequestDecoder())
            p.addLast("httpAggregator", HttpObjectAggregator(1048576))
            p.addLast("httpEncoder", HttpResponseEncoder() as ChannelHandler)
            p.addLast("handler", RequestHandler())
            p.addLast("exception", ExceptionHandler())
        }
    }

    private inner class SSLServerInitializer(val ctx: SSLContext) : ChannelInitializer<SocketChannel>() {
        override fun initChannel(ch: SocketChannel?) {
            val p = ch!!.pipeline()
            val eng = ctx.createSSLEngine()
            eng.useClientMode = false
            p.addLast("ssl", SslHandler(eng))
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
            val req = NettyRequest(this@NettyExpress, msg!!, ctx!!.channel()!!)
            val res = NettyResponse(req, ctx)
            try {
                handleRequest(req, res)
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
            this@NettyExpress.log("Exception in httpHandler!", java.util.logging.Level.WARNING, cause)

            val msg = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR)
            msg.headers().set("Content-Length", 0)
            ctx!!.writeAndFlush(msg)
            ctx.close()
        }
    }

    /**
     * Start the server listening on the given port
     */
    override fun listenSSL(port: Int, sslPort: Int) {
        bootstrap.bind(InetSocketAddress(port))

        val sslBootstrap = ServerBootstrap()
        val bossGroup = NioEventLoopGroup()
        val workerGroup = NioEventLoopGroup()
        sslBootstrap.group(bossGroup, workerGroup).
                channel(NioServerSocketChannel::class.java).
                childHandler(SSLServerInitializer(createSSLContext()))
        sslBootstrap.bind(InetSocketAddress(sslPort))

        this.log("Express listening on ports $port and $sslPort")
    }

    /**
     * Start the server listening on the given port
     */
    override fun listen(port: Int) {
        bootstrap.bind(InetSocketAddress(port))
        this.log("Express listening on port " + port)
    }
}