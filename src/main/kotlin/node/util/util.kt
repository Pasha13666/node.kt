package node.util

import node.Configuration
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.KeyStore
import java.text.SimpleDateFormat
import java.util.*
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext

fun String.extension() = if ('.' in this) substring(lastIndexOf('.') + 1) else null


fun String.encodeUriComponent() = URLEncoder.encode(this, "UTF-8").replace("+", "%20")
        .replace("%21", "!").replace("%27", "'").replace("%28", "(").replace("%29", ")")
        .replace("%7E", "~")

fun String.decodeUriComponent() = URLDecoder.decode(this, "UTF-8")!!


val httpFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ENGLISH)
        .apply { timeZone = TimeZone.getTimeZone("GMT") }

fun Date.asHttpFormatString() = httpFormat.format(this)!!
fun String.asHttpDate() = httpFormat.parse(this)!!


fun createSSLContext(): SSLContext {
    val key = (Configuration["ssl.password"] as String).toCharArray()
    val ctx = SSLContext.getInstance("TLS")
    val ks = KeyStore.getInstance(Configuration["ssl.key type"] as String)
    ks.load(File(Configuration["ssl.key"] as String).inputStream(), key)
    val kmf = KeyManagerFactory.getInstance("SunX509")
    kmf.init(ks, key)
    ctx.init(kmf.keyManagers, null, null)
    return ctx
}


fun getMessage(k: Int) = when (k) {
    100 -> "Continue"
    101 -> "Switching Protocols"
    102 -> "Processing"
    200 -> "Ok"
    201 -> "Created"
    202 -> "Accepted"
    203 -> "Non-Authoritative Information"
    204 -> "No Content"
    205 -> "Reset Content"
    206 -> "Partial Content"
    207 -> "Multi-Status"
    226 -> "IM Used"
    300 -> "Multiple Choices"
    301 -> "Moved Permanently"
    302 -> "Found"
    303 -> "See Other"
    304 -> "Not Modified"
    305 -> "Use Proxy"
    307 -> "Temporary Redirect"
    400 -> "Bad Request"
    401 -> "Unauthorized"
    402 -> "Payment Required"
    403 -> "Forbidden"
    404 -> "Not Found"
    405 -> "Method Not Allowed"
    406 -> "Not Acceptable"
    407 -> "Proxy Authentication Required"
    408 -> "Request Timeout"
    409 -> "Conflict"
    410 -> "Gone"
    411 -> "Length Required"
    412 -> "Precondition Failed"
    413 -> "Request Entity Too Large"
    414 -> "Request-URL Too Long"
    415 -> "Unsupported Media Type"
    416 -> "Requested Range Not Satisfiable"
    417 -> "Expectation Failed"
    418 -> "I'm a teapot"
    422 -> "Unprocessable Entity"
    423 -> "Locked"
    424 -> "Failed Dependency"
    425 -> "Unordered Collection"
    426 -> "Upgrade Required"
    428 -> "Precondition Required"
    429 -> "Too Many Requests"
    431 -> "Request Header Fields Too Large"
    434 -> "Requested host unavailable"
    449 -> "Retry With"
    451 -> "Unavailable For Legal Reasons"
    500 -> "Internal Server Error"
    501 -> "Not Implemented"
    502 -> "Bad Gateway"
    503 -> "Service Unavailable"
    504 -> "Gateway Timeout"
    505 -> "HTTP Version Not Supported"
    506 -> "Variant Also Negotiates"
    507 -> "Insufficient Storage"
    509 -> "Bandwidth Limit Exceeded"
    510 -> "Not Extended"
    511 -> "Network Authentication Required"
    else -> "Unknown Status"
}
