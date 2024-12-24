package co.nqb8.auth

import co.nqb8.config.AuthenticationPolicy
import co.nqb8.config.JwtPolicy
import co.nqb8.config.KeyPolicy
import co.nqb8.utils.uriMatches
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.pipeline.*
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

val AuthorizationCallPhase = PipelinePhase("GatewayAuthorizationCallPhase")
private object AuthorizationCall: Hook<suspend (ApplicationCall) -> Unit> {
    override fun install(pipeline: ApplicationCallPipeline, handler: suspend (ApplicationCall) -> Unit) {
        pipeline.insertPhaseAfter(AuthenticationCallPhase, AuthorizationCallPhase)
        pipeline.intercept(AuthorizationCallPhase) { handler(call) }
    }
}

val GatewayAuthorization = createApplicationPlugin("GatewayAuthorization"){

    on(AuthorizationCall){ call ->
        for (registry in AuthProviders){
            if (call.isHandled) return@on
            if (!uriMatches(registry.key, call.request.uri)) continue
            authorize(call, registry.value, registry.key)
        }
    }
}

suspend fun authorize(call: ApplicationCall, policy: AuthenticationPolicy, uri: String) {
    val method = call.request.httpMethod.value
    when (policy) {
        is JwtPolicy -> {
            val decoded = call.attributes[GatewayJWTAuthForCallKey]
            println()
            println(decoded.claims)
            println(decoded.getClaim(policy.check).asString())
            println()
            val routeClaim = decoded.getClaim(uri).asList(String::class.java)
            if (routeClaim == null || !routeClaim.contains(method)) {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "You do not have required permission"))
                return
            }

            checkUser(
                uri = uri,
                policy = policy,
                call = call,
                toCompare = { decoded.getClaim(policy.check).asString() }
            )
        }
        is KeyPolicy -> {
            val obj = call.attributes[GatewayKeyAuthForCallKey]
            if (policy.permissionsKeys != null){
                val (pathKey, methodKey) = policy.permissionsKeys
                val pathIndex = obj[pathKey]?.jsonArray?.map { it.jsonPrimitive.content }?.toList()?.indexOf(uri)
                if (pathIndex == null || pathIndex == -1){
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "You do not have required permission"))
                    return
                }
                val methods = obj[methodKey]!!.jsonArray[pathIndex].jsonArray.map { it.jsonPrimitive.content }.toList()
                if (!methods.contains(method)){
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "You do not have required permission"))
                    return
                }
            }
            if (policy.permissionsKey != null){
                val permissions = obj[policy.permissionsKey]!!.jsonObject[uri]?.jsonArray
                if (permissions == null || permissions.contains(JsonPrimitive(method))){
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "You do not have required permission"))
                    return
                }
            }

            checkUser(
                uri = uri,
                policy = policy,
                call = call,
                toCompare = { obj[policy.check]?.jsonPrimitive?.content }
            )
        }
    }

}

private suspend fun checkUser(
    uri: String,
    policy: AuthenticationPolicy,
    call: ApplicationCall,
    toCompare: () -> String?
) {
    val registeredUriIndex = Url(uri).segments.indexOf("{${policy.check}}")
    println("Index: $registeredUriIndex")
    if (registeredUriIndex != -1) {
        val callData = Url(call.request.uri).segments[registeredUriIndex]
        println("To compare: ${toCompare()}, call: $callData")
        if (callData != toCompare()) {
            call.respond(HttpStatusCode.Forbidden, mapOf("error" to "You do not have required permission for this route"))
            return
        }

    }
}