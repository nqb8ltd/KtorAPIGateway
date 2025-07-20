package co.nqb8.plugins

import co.nqb8.auth.GatewayAuthentication
import co.nqb8.auth.GatewayAuthorization
import co.nqb8.config.ServiceException
import co.nqb8.config.adminTokens
import co.nqb8.data.RequestRepository
import co.nqb8.ratelimiter.GatewayRateLimit
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.util.*
import kotlinx.serialization.json.Json
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

fun Application.configurePlugins() {
    callId()
    rateLimit()
    cors()
    statusPages()
    contentNegotiation()
    auth()
}

private fun Application.auth() {
    install(GatewayAuthentication)
    install(GatewayAuthorization)
    val apikey = environment.config.property("gateway.apikey").getString()
    log.info("API KEY: $apikey")
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
        bearer("admin-auth"){
            authenticate { tokenCredential ->
                if (adminTokens.contains(tokenCredential.token)) {
                    UserIdPrincipal("admin")
                }else null
            }
        }
    }
}

private fun Application.cors() {
    install(CORS) {
        anyHost()
        anyMethod()
        allowCredentials = true
        allowNonSimpleContentTypes = true
        allowSameOrigin = true
        allowHeaders { true }
    }
}

private fun Application.rateLimit() {
    install(GatewayRateLimit)

}

@OptIn(ExperimentalUuidApi::class)
private fun Application.callId() {
    install(CallId){
        generate { Uuid.random().toString() }
        header(HttpHeaders.XRequestId)
        verify { callId: String -> callId.isNotEmpty() }
    }
    val requestRepository = RequestRepository(this)
    attributes.put(AttributeKey(RequestRepository.KEY), requestRepository)
    install(RequestLogging)
}

private fun Application.statusPages() {
    install(StatusPages) {
        status(HttpStatusCode.TooManyRequests) { call, status ->
            val retryAfter = call.response.headers["Retry-After"]
            call.respondText(text = "429: Too many requests. Wait for $retryAfter seconds.", status = status)
        }
        exception<ServiceException>{ call, cause ->
            call.respondError(cause.message.orEmpty())
        }
        exception<Throwable> { call, cause ->
            call.respondText(text = "500: $cause", status = HttpStatusCode.InternalServerError)
        }
    }
}

private fun Application.contentNegotiation() {
    install(ContentNegotiation) {
        json(json = Json {
            ignoreUnknownKeys = true
            this.prettyPrint = true
        })
    }
}

val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
}