package node.express

import node.NotFoundException
import node.express.engines.FreemarkerEngine
import node.express.engines.VelocityEngine
import node.util.extension
import node.util.log
import java.io.File
import java.io.FileNotFoundException
import java.util.*
import java.util.logging.Level


// The default error handler. Can be overridden by setting the errorHandler property of the
// Express instance
var defaultErrorHandler :((Throwable, Request, Response) -> Unit) = { t, req, res ->
  log(Level.WARNING, "Error thrown handling request: " + req.path, t)
  when (t) {
    is ExpressException -> res.send(t.code, t.message)
    is NotFoundException -> res.notFound()
    is FileNotFoundException -> res.notFound()
    is IllegalArgumentException -> res.badRequest()
    is IllegalAccessException -> res.forbidden()
    is IllegalAccessError -> res.forbidden()
    is UnsupportedOperationException -> res.notImplemented()
    else -> res.internalServerError()
  }
}

class RouteHandler(val req: Request, val res: Response)

/**
 * Express.kt
 */
abstract class Express {
  private val settings = HashMap<String, Any>()
  val locals:MutableMap<String, Any> = HashMap()
  private val routes = ArrayList<Route>()
  private val engines = HashMap<String, Engine>()

  val params = HashMap<String, (Request, Response, Any) -> Any?>()

  var errorHandler: ((Throwable, Request, Response) -> Unit) = defaultErrorHandler

  init {
    settings.put("views", "views/")
    settings.put("jsonp callback name", "callback")

    locals.put("settings", settings)

    engines.put("vm", VelocityEngine())
    engines.put("ftl", FreemarkerEngine())
  }

  /**
   * Assign a setting value. Settings are available in templates as 'settings'
   */
  fun set(name: String, value: Any) {
    settings.put(name, value)
  }

  /**
   * Get the value of a setting
   */
  fun get(name: String): Any? {
    return settings[name]
  }

  /**
   * Register a rendering engine with an extension. Once registered, setting the 'view engine' setting
   * to a registered extension will set the default view engine. Then, when calling 'render', if no
   * file extension is part of the name, the default extension will be appended and the appropriate
   * rendering engine will be used.
   * @param ext the file extension to register (ie. vm, flt, etc.)
   * @param engine a rendering engine that implements the Engine trait
   */
  fun engine(ext: String, engine: Engine) {
    engines.put(ext, engine)
  }

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

    fun render(name: String, vararg ent: Pair<String, Any?>) = render(name, mapOf(*ent))

  /**
   * Render a view, returning the resulting string.
   * @param name the name of the view. If the extension is left off, the value of 'view engine' will
   * be used as the extension.
   * @param data data that will be passed to the rendering engine to be used in the rendering of the
   * view. This data will be merged with 'locals', allowing you to set global data to be used in
   * all rendering operations.
   */
  fun render(name: String, data: Map<String, Any?>? = null): String {
    var ext = name.extension()
    var viewFileName = name
    if (ext == null) {
      ext = settings["view engine"] as? String ?: throw IllegalArgumentException("No default view set for view without extension")
      viewFileName = name + "." + ext
    }

    val renderer = engines[ext] ?: throw IllegalArgumentException("No renderer for ext: " + ext)

    val mergedContext = HashMap<String, Any?>()
    mergedContext.putAll(locals)
    if (data != null)
      mergedContext.putAll(data)

    val viewsPath = settings["views"] as String

    val viewFile = File(viewsPath + viewFileName)
    if (!viewFile.exists())
      throw FileNotFoundException()
    val viewPath = viewFile.absolutePath

    return renderer.render(viewPath, mergedContext)
  }

  fun install(method: String, path: String, vararg handler: RouteHandler.() -> Boolean) {
    handler.forEach {
      routes.add(Route(method, path, it))
    }
  }

  private fun installAll(path: String, vararg handler: RouteHandler.() -> Boolean) {
    install("get", path, *handler)
    install("put", path, *handler)
    install("post", path, *handler)
    install("delete", path, *handler)
    install("head", path, *handler)
    install("patch", path, *handler)
  }

  /**
   * Install a middleware Handler object to be used for all requests.
   */
  fun use(middleware: RouteHandler.() -> Boolean) {
    install("*", "*", middleware)
  }

  /**
   * Install middleware for a given path expression
   */
  fun use(path: String, vararg middleware: RouteHandler.() -> Boolean) {
    install("*", path, *middleware)
  }

  /**
   * Install a handler for a path for all HTTP methods.
   */
  fun all(path: String, vararg middleware: RouteHandler.() -> Boolean) {
    install("*", path, *middleware)
  }

  /**
   * Install a GET handler callback for a path
   */
  fun get(path: String, middleware: RouteHandler.() -> Boolean) {
    install("get", path, middleware)
  }

  /**
   * Install a POST handler callback for a path
   */
  fun post(path: String, vararg middleware: RouteHandler.() -> Boolean) {
    install("post", path, *middleware)
  }

  /**
   * Install a PATCH handler callback for a path
   */
  fun patch(path: String, vararg middleware: RouteHandler.() -> Boolean) {
    install("patch", path, *middleware)
  }

  /**
   * Install a PUT handler callback for a path
   */
  fun put(path: String, vararg middleware: RouteHandler.() -> Boolean) {
    install("put", path, *middleware)
  }

  /**
   * Install a DELETE handler callback for a path
   */
  fun delete(path: String, vararg middleware: RouteHandler.() -> Boolean) {
    install("delete", path, *middleware)
  }

  /**
   * Install a HEAD handler callback for a path
   */
  fun head(path: String, vararg middleware: RouteHandler.() -> Boolean) {
    install("head", path, *middleware)
  }

  fun handleRequest(req: Request, res: Response, stackIndex: Int = 0) {
      val rh = RouteHandler(req, res)
      for (i in routes.stream().skip(stackIndex.toLong()).filter { req.checkRoute(it, res) })
          if (i.handler.invoke(rh)) return
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
open class ExpressException(val code: Int, msg: String? = null, cause: Throwable? = null): Exception(msg, cause)

/**
 * Exception to throw whenever the request is invalid
 */
open class BadRequest(msg: String? = null, cause: Throwable? = null): ExpressException(400, msg, cause)