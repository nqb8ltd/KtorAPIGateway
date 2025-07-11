package co.nqb8.auth

import co.nqb8.config.AuthenticationPolicy
import co.nqb8.config.JwtPolicy
import co.nqb8.config.KeyPolicy
import co.nqb8.data.dto.AuthType
import co.nqb8.data.requestRepository
import co.nqb8.forwarder.Forwarder
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import io.ktor.client.call.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.response.*
import io.ktor.util.collections.*
import kotlinx.serialization.json.JsonObject

suspend fun authenticate(
    call: ApplicationCall,
    policy: AuthenticationPolicy,
    forwarder: Forwarder,
    registry: ConcurrentMap<String, JsonObject>
) {
    when (policy) {
        is JwtPolicy -> verifyJwt(call, policy)
        is KeyPolicy -> verifyKey(call, policy, forwarder, registry)
    }
}

private suspend fun verifyKey(
    call: ApplicationCall,
    policy: KeyPolicy,
    forwarder: Forwarder,
    registry: ConcurrentMap<String, JsonObject>
) {
    val key = call.request.headers[policy.keyHeader]
    if (key == null) {
        call.respond(HttpStatusCode.Forbidden)
        return
    }

    val cached = registry[key]
    if (cached != null) {
        call.attributes.put(GatewayKeyAuthForCallKey, cached)
        return
    }

    val response = forwarder.route(
        path = policy.verifyEndpoint,
        methodType = "GET",
        heads = Headers.build { append(policy.keyHeader, key) },
        origin = call.request.origin.remoteAddress
    )
    if (!response.status.isSuccess()){
        call.application.requestRepository.update(call.callId) {
            it.authType = AuthType.KEY
            it.authenticationSuccess = false
        }
        call.respond(HttpStatusCode.Forbidden)
        return
    }

    val result = response.body<JsonObject>()
    registry[key] = result
    call.attributes.put(GatewayKeyAuthForCallKey, result)

    call.application.requestRepository.update(call.callId) {
        it.authType = AuthType.KEY
        it.authenticationSuccess = true
    }

}
private suspend fun verifyJwt(call: ApplicationCall, policy: JwtPolicy) {
    val bearer = call.request.headers[HttpHeaders.Authorization]
    if (bearer == null || !bearer.startsWith("Bearer ")) {
        call.respond(HttpStatusCode.Unauthorized)
        return
    }
    val token = bearer.substringAfter("Bearer ")
    when(policy.policy){
        AuthenticationPolicy.Policy.VERIFY -> {
            verifyJwt(call, token, policy)
        }
        AuthenticationPolicy.Policy.PRESENT ->{}
    }
}

private suspend fun verifyJwt(call: ApplicationCall, jwt: String, policy: JwtPolicy) {
    val verifier = JWT.require(Algorithm.HMAC256(policy.jwtSecret))
        .build()
    val requestRepository = call.application.requestRepository
    try {
        val verified = verifier.verify(jwt)
        call.attributes.put(GatewayJWTAuthForCallKey, verified)
        requestRepository.update(call.callId) {
            it.authType = AuthType.JWT
            it.authenticationSuccess = true
        }
    } catch (e: JWTVerificationException) {
        requestRepository.update(call.callId){
            it.authType = AuthType.JWT
            it.authenticationSuccess = false
        }
        val error = mapOf("error" to e.message)
        call.respond(HttpStatusCode.Unauthorized, error)
    }
}

