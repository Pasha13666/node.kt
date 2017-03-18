package node.express.session

import node.express.Request
import node.express.Response

/**
 * A session that is no session at all and just stored data in memory
 * for the duration of the request
 */
class MemorySession : Session {
    companion object : SessionFactory {
        private val session = MemorySession()
        override fun getSession(req: Request, res: Response): Session = session
    }

    val store = HashMap<String, Any>()

    override fun get(key: String): Any? {
        return store[key]
    }

    override fun set(key: String, value: Any) {
        store[key] = value
    }

    override fun save() {
        // do nothing
    }

    override fun clear() {
        store.clear()
    }
}
