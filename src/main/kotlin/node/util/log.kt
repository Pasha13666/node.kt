package node.util

import org.slf4j.LoggerFactory
import java.util.logging.Level

private fun getLogger(a: Any?) = if (a == null) LoggerFactory.getLogger("ROOT")!!
    else LoggerFactory.getLogger(a.javaClass)!!

/**
 * Attaches a log function to any object, and uses that object's class as the
 * logging context.
 */
fun Any.log(message: Any, level: Level = Level.INFO, t: Throwable? = null) {
    val logger = getLogger(this)
    val msg = message.toString()
    when (level) {
        Level.INFO -> logger.info(msg, t)
        Level.SEVERE -> logger.error(msg, t)
        Level.WARNING -> logger.warn(msg, t)
        Level.FINE -> logger.debug(msg, t)
        Level.FINER -> logger.debug(msg, t)
        Level.FINEST -> logger.debug(msg, t)
        else -> logger.info(msg, t)
    }
}

fun log(message: Any) {
    getLogger(null).info(message.toString())
}

fun log(l: Level, msg: String, t: Throwable? = null) {
    val logger = getLogger(null)
    when (l) {
        Level.INFO -> logger.info(msg, t)
        Level.SEVERE -> logger.error(msg, t)
        Level.WARNING -> logger.warn(msg, t)
        Level.FINE -> logger.debug(msg, t)
        Level.FINER -> logger.debug(msg, t)
        Level.FINEST -> logger.debug(msg, t)
        else -> logger.info(msg, t)
    }
}
