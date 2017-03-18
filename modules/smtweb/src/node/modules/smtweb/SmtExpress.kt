package node.modules.smtweb

import node.express.Express
import node.express.HttpVersion
import node.util.createSSLContext
import node.util.log
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.Executors
import java.util.logging.Level

class SmtExpress : Express() {
    private val server = ServerSocket()
    private val pool = Executors.newFixedThreadPool(4)
    private var sslServerThread: Thread? = null
    private val serverThread = Thread {
        handleAll(server)
    }

    private fun handleAll(ss: ServerSocket){
        while (!Thread.interrupted())
            try {
                val sock = ss.accept()!!
                pool.submit {
                    try {
                        handleRequest(sock)
                    } catch (e: SocketTimeoutException){
                        try {
                            sock.close()
                        } catch (e: IOException){
                            // ignore
                        }
                        log(Level.SEVERE, "Connection to ${sock.inetAddress} timed out")
                    } catch (e: Exception){
                        log(Level.WARNING, "Request error!", e)
                    }
                }
            } catch (e: InterruptedException){
                return
            } catch (e: Exception){
                log(Level.WARNING, "Accept() error!", e)
            }
    }

    private fun handleRequest(sock: Socket){
        val input = sock.getInputStream().bufferedReader(Charsets.UTF_8)
        val output = sock.getOutputStream()
        val remote = sock.inetAddress.hostAddress
        var alive = true

        while (alive) {
            sock.soTimeout = 5000
            val fl = (input.readLine() ?: return).split(' ', limit = 3)
            sock.soTimeout = 60000
            if (fl.size != 2 && fl.size != 3) return

            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = input.readLine() ?: return
                if (line.isEmpty())
                    break
                val l = line.split(": ", limit = 2)
                if (l.size != 2) return
                headers.put(l[0], l[1])
            }

            val req = SmtRequest(this, fl[0], fl[1], fl.getOrNull(2), remote, headers, input)
            val res = SmtResponse(req, output)
            res.on("header"){
                if ((req.version == HttpVersion.HTTP_10 && req.header("Connection")?.toLowerCase()
                        == "keep-alive") || (req.version == HttpVersion.HTTP_11 && req.header("Connection")
                        ?.toLowerCase() != "close") || req.version == HttpVersion.HTTP_20){

                    res.headers["Connection"] = "Keep-Alive"
                } else alive = false
            }
            handleRequest(req, res)
        }

        try {
            output.close()
            input.close()
            sock.close()
        } catch (e: IOException){
            // ignore
        }
    }

    /**
     * Start the server listening on the given port
     */
    override fun listen(port: Int) {
        server.bind(InetSocketAddress(port))
        serverThread.start()
        this.log("Express listening on port " + port)
    }

    /**
     * Start the server listening on the given port
     */
    override fun listenSSL(port: Int, sslPort: Int) {
        server.bind(InetSocketAddress(port))
        serverThread.start()

        val sslServer = createSSLContext().serverSocketFactory.createServerSocket()
        sslServer.bind(InetSocketAddress(sslPort))
        sslServerThread = Thread {
            handleAll(sslServer)
        }
        sslServerThread!!.start()

        this.log("Express listening on ports $port and $sslPort")
    }

    override fun stop(){
        serverThread.interrupt()
        sslServerThread?.interrupt()
        serverThread.join()
        sslServerThread?.join()
    }
}