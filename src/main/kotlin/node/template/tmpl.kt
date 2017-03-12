package node.template

/**
 * A version of a while loop that takes the string output of each iteration
 * and concatenates it and returns it.
 */
fun While(eval: ()->Boolean, out: ()->String):String {
  val sb = StringBuilder()
  while (eval())
      sb.append(out())
  return sb.toString()
}

/**
 * A version of a for loop that takes the string output of each iteration
 * and concatenates it and returns it
 */
fun<T> For(iterator: Iterable<T>, out: (v:T)->String): String {
  return For(iterator.iterator(), out)
}

/**
 * A version of a for loop that takes the string output of each iteration
 * and concatenates it and returns it
 */
fun<T> For(iterator: Iterator<T>, out: (v:T)->String?): String {
  val sb = StringBuilder()
  iterator.forEach { sb.append(out(it)) }
  return sb.toString()
}