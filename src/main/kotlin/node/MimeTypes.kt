package node

import java.io.File

/**
 * Mime type calls
 */
val mimes = hashMapOf(
    "png" to "image/png",
    "jpg" to "image/jpeg",
    "txt" to "text/plain",
    "html" to "text/html",
    "json" to "application/json",
    "gif" to "image/gif",
    "css" to "text/css",
    "js" to "application/javascript"
)

fun File.mimeType(): String {
  return mimes[this.extension] ?: "application/octet-stream"
}

fun String.mimeType(): String {
  return mimes[this] ?: "application/octet-stream"
}
