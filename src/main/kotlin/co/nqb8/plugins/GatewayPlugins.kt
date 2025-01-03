package co.nqb8.plugins

import co.nqb8.auth.GatewayAuthentication
import co.nqb8.auth.GatewayAuthorization
import co.nqb8.ratelimiter.GatewayRateLimit
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import kotlinx.serialization.json.Json

fun Application.configurePlugins() {
    rateLimit()
    cors()
    statusPages()
    contentNegotiation()
    auth()
}

private fun Application.auth(){
    install(GatewayAuthentication)
    install(GatewayAuthorization)
    val apikey = environment.config.property("gateway.apikey").getString()
    install(Authentication) {

        bearer("gateway-auth") {
            realm = "Access to the '/_' path"
            authenticate { tokenCredential ->
                if (tokenCredential.token == apikey) {
                    UserIdPrincipal("verified")
                } else {
                    null
                }
            }
        }
    }
}
private fun Application.cors() {
    install(CORS){
        anyHost()
        anyMethod()
        allowCredentials = true
        allowNonSimpleContentTypes = true
        allowSameOrigin = true
    }
}
private fun Application.rateLimit() {
    install(GatewayRateLimit)
}

private fun Application.statusPages() {
    install(StatusPages) {
        status(HttpStatusCode.TooManyRequests) { call, status ->
            val retryAfter = call.response.headers["Retry-After"]
            call.respondText(text = "429: Too many requests. Wait for $retryAfter seconds.", status = status)
        }
        exception<Throwable> { call, cause ->
            call.respondText(text = "500: $cause" , status = HttpStatusCode.InternalServerError)
        }
    }
}

private fun Application.contentNegotiation() {
    install(ContentNegotiation){
        json(json = Json {
            ignoreUnknownKeys = true
            this.prettyPrint = true
        })
    }
}