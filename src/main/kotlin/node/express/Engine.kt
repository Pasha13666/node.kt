package node.express

import java.io.Writer

/**
 * An Express rendering engine
 */
interface Engine {
  /**
   * Render a page
   * @param path a path to the template
   * @param data data that is passed to the page
   */
  fun render(path: String, data: Map<String, *>, out: Writer)
}