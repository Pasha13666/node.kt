package node.express

import java.util.ArrayList
import java.util.regex.Pattern
import java.util.regex.Matcher
import java.util.HashMap
import java.net.URLDecoder

/**
 * Describes an installed route.
 */
class Route(val method: String, val path: String, val handler: RouteHandler.() -> Boolean) {
  private class Key(val name: String, val optional: Boolean)

  lateinit var pattern: Pattern
  private var keys = ArrayList<Key>()

  init {
    buildPathRegEx(path, true)
  }

  // Takes a simplified path and converts it to a RegEx that we can
  // use to filter requests
  fun buildPathRegEx(path: String, strict: Boolean) {
    val keys = ArrayList<Key>()
    val p = if (strict) path else "/?"

    val sb = StringBuffer()
    val pattern = Pattern.compile("(/)?(\\.)?:(\\w+)(?:(\\(.*?\\)))?(\\?)?(\\*)?")
    val matcher = pattern.matcher(p)
    while (matcher.find()) {
      val slash = matcher.group(1) ?: ""
      val format = matcher.group(2)
      val key = matcher.group(3)
      val capture = matcher.group(4)
      val optional = matcher.group(5)
      val star = matcher.group(6)

      if (key != null)
        keys.add(Key(key, optional != null))

      var replacement = ""
      if (optional == null) replacement += slash
      if (format != null) replacement += format

      if (capture != null) {
        replacement += capture
      } else if (format != null) {
        replacement += "([^/.]+?)"
      } else {
        replacement += "([^/]+?)"
      }
      //            replacement += ")";
      if (optional != null) replacement += optional

      if (star != null) replacement += "(/*)?"

      matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement))
    }
    matcher.appendTail(sb)
    var regex = sb.toString()
    regex = regex.replace("([/.])".toRegex(), "\\/")
    regex = regex.replace("\\*".toRegex(), "(.*)")
    regex = "^$regex$"
    this.pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE)
    this.keys = keys
  }

  /**
   * Match a request against the path specification, returning
   * a map of parameters
   */
  fun match(req: Request, res: Response): Map<String, Any>? {
    if (req.method != method && method != "*")
      return null // no match based on the method

    val path = req.path
    val matcher = pattern.matcher(path)

    if (matcher.matches()) {
      val result = HashMap<String, Any>()
      val count = matcher.groupCount()
      for (i in 1..count) {
        val value = matcher.group(i)
        if (value != null) {
          val decodedValue = URLDecoder.decode(value, "UTF-8")
          if (keys.size >= i) {
            val key = keys[i - 1].name

            // now check with the app for any parameter mapping functions
            val mapper = req.app.params[key]
            if (mapper != null) {
              val mapValue = mapper(req, res, decodedValue)
              if (mapValue != null) {
                result.put(key, mapValue)
              }
            } else {
              result.put(key, decodedValue)
            }
          } else {
            result.put("*", decodedValue)
          }
        }
      }
      return result
    } else {
      return null
    }
  }
}
