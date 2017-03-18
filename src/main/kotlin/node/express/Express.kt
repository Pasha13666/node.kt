package node.express

import node.Configuration
import node.NotFoundException
import node.util.log
import java.io.FileNotFoundException
import java.util.*
import java.util.logging.Level


// The default error handler. Can be overridden by setting the errorHandler property of the
// Express instance
var defaultErrorHandler: ((Throwable, Request, Response) -> Unit) = { t, req, res ->
    log(Level.WARNING, "Error thrown handling request: " + req.path, t)
    when (t) {
        is ExpressException -> {
            res.status = t.code
            res.sendOnly(t.message ?: "")
        }
        is NotFoundException -> res.notFound()
        is FileNotFoundException -> res.notFound()
        is IllegalArgumentException -> res.badRequest()
        is IllegalAccessException -> res.forbidden()
        is IllegalAccessError -> res.forbidden()
        is UnsupportedOperationException -> res.notImplemented()
        else -> res.internalServerError()
    }
}

enum class HttpVersion(val v_name: String) {
    HTTP_09("HTTP/0.9"),
    HTTP_10("HTTP/1.0"),
    HTTP_11("HTTP/1.1"),
    HTTP_20("HTTP/2"),
    ;
    companion object {
        operator fun get(name: String?): HttpVersion {
            if (name == null) return HTTP_09
            val aName = name.toUpperCase()
            return values().firstOrNull { it.v_name == aName } ?: HTTP_09
        }
    }

    override fun toString() = v_name
}

data class RouteHandler(val req: Request, val res: Response)

/**
 * Express.kt
 */
abstract class Express {
    private val settings = HashMap<String, Any>()
    private val routes = ArrayList<Route>()
    val engines = HashMap<String, Engine>()
    val locals: MutableMap<String, Any> = HashMap()
    val params = HashMap<String, (Request, Response, Any) -> Any?>()
    var errorHandler: ((Throwable, Request, Response) -> Unit) = defaultErrorHandler

    init {
        settings.put("views", "views/")
        settings.put("jsonp callback name", "callback")
        settings.putAll(Configuration.map("server"))

        locals.put("settings", settings)
    }

    fun start(){
        val port = Configuration["server.port"] as? Int ?: 80
        if (Configuration["ssl.enable"] as? Boolean ?: false)
            listenSSL(port, Configuration["ssl.port"] as? Int ?: 446)
        else listen(port)
    }

    abstract fun stop()
    protected abstract fun listen(port: Int)
    protected open fun listenSSL(port: Int, sslPort: Int){
        log(Level.WARNING, "Cant start https server -- starting http.")
        listen(port)
    }

    /**
     * Assign a setting value. Settings are available in templates as 'settings'
     */
    operator fun set(name: String, value: Any) {
        settings[name] = value
    }

    /**
     * Get the value of a setting
     */
    operator fun get(name: String): Any? = settings[name]

    /**
     * Set a feature setting to 'true'. Identical to [[Express.set(feature, true)]].
     */
    fun enable(feature: String) {
        settings.put(feature, true)
    }

    /**
     * Set a feature setting to 'false'. Identical to [[Express.set(feature, false)]].
     */
    fun disable(feature: String) {
        settings.put(feature, false)
    }

    /**
     * Check if a setting is enabled. If the setting doesn't exist, returns false.
     */
    fun enabled(feature: String): Boolean {
        return settings[feature] as? Boolean ?: false
    }

    /**
     * Map logic to route parameters. When a provided parameter is present,
     * calls the builder function to assign the value. So, for example, the :user
     * parameter can be mapped to a function that creates a user object.
     */
    fun param(name: String, builder: (Request, Response, Any) -> Any?) {
        params.put(name, builder)
    }

    fun install(method: String, path: String, handler: RouteHandler.() -> Boolean) {
        routes.add(Route(method, path, handler))
    }

    /**
     * Install a middleware Handler object to be used for all requests.
     */
    fun use(middleware: RouteHandler.() -> Boolean) = install("*", "*", middleware)

    /**
     * Install middleware for a given path expression
     */
    fun use(path: String, middleware: RouteHandler.() -> Boolean) = install("*", path, middleware)

    /**
     * Install a handler for a path for all HTTP methods.
     */
    fun all(path: String, middleware: RouteHandler.() -> Boolean) = install("*", path, middleware)

    /**
     * Install a GET handler callback for a path
     */
    fun get(path: String, middleware: RouteHandler.() -> Boolean) = install("get", path, middleware)

    /**
     * Install a POST handler callback for a path
     */
    fun post(path: String, middleware: RouteHandler.() -> Boolean) = install("post", path, middleware)

    /**
     * Install a PATCH handler callback for a path
     */
    fun patch(path: String, middleware: RouteHandler.() -> Boolean) = install("patch", path, middleware)

    /**
     * Install a PUT handler callback for a path
     */
    fun put(path: String, middleware: RouteHandler.() -> Boolean) = install("put", path, middleware)

    /**
     * Install a DELETE handler callback for a path
     */
    fun delete(path: String, middleware: RouteHandler.() -> Boolean) = install("delete", path, middleware)

    /**
     * Install a HEAD handler callback for a path
     */
    fun head(path: String, middleware: RouteHandler.() -> Boolean) = install("head", path, middleware)

    fun handleRequest(req: Request, res: Response) {
        val rh = RouteHandler(req, res)
        if (routes.firstOrNull { req.checkRoute(it, res) && it.handler.invoke(rh) } != null)
            return
        res.sendErrorResponse(404)
    }

//
//  //***************************************************************************
//  // WEBSOCKET SUPPORT
//  //***************************************************************************
//
//  // Map of channel identifiers to WebSocketHandler instances
//  private val webSocketHandlers = HashMap<Int, WebSocketHandler>()
//
//  /**
//   * Install web socket route
//   */
//  fun webSocket(path: String, handler: (WebSocketChannel) -> WebSocketHandler) {
//    val route = WebSocketRoute(path)
//    get(path, { req, res, next ->
//      route.handshake(req.channel, req.request)
//      val wsh = handler(WebSocketChannel(req.channel))
//      webSocketHandlers.put(req.channel.getId()!!, wsh)
//      req.channel.getCloseFuture()!!.addListener(object: ChannelFutureListener {
//        public override fun operationComplete(future: ChannelFuture?) {
//          wsh.closed()
//          webSocketHandlers.remove(future!!.channel()!!.getId())
//        }
//      })
//    })
//  }
//
//  /**
//   * Defines a WebSocket route. Mainly responsible for the initial handshake.
//   */
//  class WebSocketRoute(val path: String) {
//    val channelGroup = DefaultChannelGroup(path)
//    var wsFactory: WebSocketServerHandshakerFactory? = null
//
//    fun handshake(channel: Channel, req: HttpRequest) {
//      val location = "ws://" + req.headers().get("host") + QueryStringDecoder(req.getUri()!!)
//      if (wsFactory == null) {
//        wsFactory = WebSocketServerHandshakerFactory(location, null, false)
//      }
//      val handshaker = wsFactory!!.newHandshaker(req);
//      if (handshaker == null) {
//        wsFactory!!.sendUnsupportedWebSocketVersionResponse(channel)
//      } else {
//        channelGroup.add(channel)
//        handshaker.handshake(channel, req)
//      }
//    }
//  }
//
//
//  /**
//   * Handle a web socket request. Mainly passes along data to a WebSocketHandler.
//   */
//  private fun handleWebSocketRequest(channel: Channel, frame: WebSocketFrame) {
//    val handler = webSocketHandlers.get(channel.getId())
//    if (handler == null) return
//
//    handler.handle(channel, frame)
//    if (frame is CloseWebSocketFrame) {
//      webSocketHandlers.remove(channel.getId())
//    }
//  }
}

/**
 * Exception thrown by Express
 */
open class ExpressException(val code: Int, msg: String? = null, cause: Throwable? = null) : Exception(msg, cause)

