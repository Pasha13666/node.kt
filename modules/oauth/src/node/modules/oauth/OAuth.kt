package node.modules.oauth

import node.NotFoundException
import node.Configuration
import node.express.Express
import node.express.Request
import node.express.Response
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.utils.URLEncodedUtils
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager
import org.apache.http.message.BasicNameValuePair
import org.apache.http.util.EntityUtils
import java.io.IOException
import java.net.URLEncoder
import java.util.*


/**
 * Generates random strings of a given length using numbers and lowercase letters.
 */
object stateGenerator {
    private val random = Random()
    private val randomSymbols: CharArray = run {
        val symbols = CharArray(36)
        for (idx in 0..9) symbols[idx] = '0' + idx
        for (idx in 10..35) symbols[idx] = 'a' + idx - 10
        symbols
    }

    fun next(): String {
        val buf = CharArray(15)
        for (idx in buf.indices) buf[idx] = randomSymbols[random.nextInt(randomSymbols.size)]
        return String(buf)
    }
}


fun encodeUriComponent(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
        .replace("%21", "!").replace("%27", "'").replace("%28", "(").replace("%29", ")")
        .replace("%7E", "~")

private val client = HttpClientBuilder.create().setConnectionManager(PoolingHttpClientConnectionManager()).build()


/**
 * An OAuth client. Create an instance, then call register for each provider. To authorize, direct the user to
 * /oauth/login/:provider and the rest is taken care of
 */
class OAuth(express: Express, localPath: String = "/oauth/login/:provider") {
    val providers = HashMap<String, OAuthProvider>()

    init {
        express.get(localPath) {
            val providerId = req.param("provider") as String
            val provider = providers[providerId] ?: throw NotFoundException()

            val scope = req.param("scope") as String?
            val redirect = "http://${req.header("Host")}/oauth/callback/$providerId"
            res.redirect("${provider.authUrl}?client_id=${provider.clientId}&redirect_uri=${encodeUriComponent(redirect)}${
            if (scope != null) "&scope=${encodeUriComponent(scope)}" else ""}&state=${provider.state}")
            true
        }

        express.get("/oauth/callback/:provider", {
            val state = req.param("state") as String
            val code = req.param("code") as String

            val provider = providers[req.param("provider") as String] ?: throw NotFoundException()
            if (provider.state != state) throw IllegalAccessException()

            val redirect = "http://${req.header("Host")}/oauth/callback/${provider.name}"
            val request = HttpPost(provider.tokenUrl).apply {
                setHeader("Accept", "*/*")
                entity = UrlEncodedFormEntity(listOf(
                        BasicNameValuePair("client_id", provider.clientId),
                        BasicNameValuePair("redirect_uri", redirect),
                        BasicNameValuePair("client_secret", provider.secret),
                        BasicNameValuePair("code", code)
                ))
            }
            val accessToken = client.execute(request).apply {
                if (statusLine!!.statusCode >= 400){
                    if (getFirstHeader("Content-Type")?.value == "text/javascript")
                        throw IOException(entity.content.reader(Charsets.UTF_8).readText())
                    EntityUtils.consume(entity)
                    throw if (statusLine!!.statusCode == 404) NotFoundException(request.uri.toString())
                    else IOException(statusLine.toString())
                }
            }.let {
                URLEncodedUtils.parse(EntityUtils.toString(it.entity!!), Charsets.UTF_8)
                        .firstOrNull { it.name == "access_token" }?.value
            }

            if (accessToken != null) {
                provider.authSuccess(req, res, accessToken)
            } else {
                provider.authFail(req, res)
            }
            true
        })
    }

    fun register(provider: OAuthProvider) {
        providers.put(provider.name, provider)
    }

    /**
     * Register a provider, loading data from configuration. Configuration should be in oauth.${providername}
     * with keys authUrl, tokenUrl, clientId, secret
     */
    fun register(name: String, authSuccess: (Request, Response, String)->Unit,
                 authFail: (Request, Response)->Unit) = register(OAuthProvider(name,
            Configuration["oauth.$name.authUrl"] as String,
            Configuration["oauth.$name.tokenUrl"] as String,
            Configuration["oauth.$name.clientId"] as String,
            Configuration["oauth.$name.secret"] as String,
            authSuccess, authFail))
}

class OAuthProvider(val name: String, val authUrl: String, val tokenUrl: String,
                    val clientId: String, val secret: String,
                    val authSuccess: (Request, Response, String)->Unit,
                    val authFail: (Request, Response)->Unit) {
    val state = stateGenerator.next()
}