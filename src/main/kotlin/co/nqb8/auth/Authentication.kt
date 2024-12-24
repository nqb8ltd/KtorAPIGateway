package co.nqb8.auth

import co.nqb8.pipeline.Forwarder
import co.nqb8.ratelimiter.GatewayCallPhase
import co.nqb8.utils.uriMatches
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.util.*
import io.ktor.util.collections.*
import io.ktor.util.pipeline.*
import kotlinx.serialization.json.JsonObject

val AuthenticationCallPhase = PipelinePhase("GatewayAuthenticationCallPhase")

private object AuthenticationCall : Hook<suspend (ApplicationCall) -> Unit> {
    override fun install(pipeline: ApplicationCallPipeline, handler: suspend (ApplicationCall) -> Unit) {
        pipeline.insertPhaseAfter(GatewayCallPhase, AuthenticationCallPhase)
        pipeline.intercept(AuthenticationCallPhase) { handler(call) }
    }
}

val GatewayAuthApiKeyRegistryKey = AttributeKey<ConcurrentMap<String, JsonObject>>("GatewayAuthApiKeyRegistryKey")


val GatewayAuthentication = createApplicationPlugin("GatewayAuthentication"){
    val registry = application.attributes.computeIfAbsent(GatewayAuthApiKeyRegistryKey) { ConcurrentMap() }
    on(AuthenticationCall){ call ->
        val forwarder = application.attributes[AttributeKey<Forwarder>("forwarder")]
        for (provider in AuthProviders){
            if (call.isHandled) return@on
            if (!uriMatches(provider.key, call.request.uri)) continue
            authenticate(call, provider.value, forwarder, registry)
        }
    }
}

