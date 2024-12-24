package co.nqb8.ratelimiter

import co.nqb8.utils.uriMatches
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*
import io.ktor.util.collections.*
import io.ktor.util.date.*
import io.ktor.util.pipeline.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


internal val GatewayRateLimitersForCallKey = AttributeKey<List<Pair<RateLimitProvide, RateLimiter>>>("GatewayRateLimitersForCallKey")

val GatewayCallPhase = PipelinePhase("GatewayRateLimitCallPhase")

private object RateLimitCall : Hook<suspend (ApplicationCall) -> Unit> {
    override fun install(pipeline: ApplicationCallPipeline, handler: suspend (ApplicationCall) -> Unit) {
        pipeline.insertPhaseBefore(ApplicationCallPipeline.Call, GatewayCallPhase)
        pipeline.intercept(GatewayCallPhase) { handler(call) }
    }
}
internal val GatewayRateLimiterInstancesRegistryKey =
    AttributeKey<ConcurrentMap<ProviderKey, RateLimiter>>("GatewayRateLimiterInstancesRegistryKey")

val providers = mutableListOf<RateLimitProvide>()


val GatewayRateLimit = createApplicationPlugin("GatewayRateLimit") {

    val registry = application.attributes.computeIfAbsent(GatewayRateLimiterInstancesRegistryKey) { ConcurrentMap() }
    val clearOnRefillJobs = ConcurrentMap<ProviderKey, Job>()

    on(RateLimitCall) { call ->
        for (provider in providers){
            if (call.isHandled) return@on
            if (!uriMatches(provider.name, call.request.uri)) continue

            val key = provider.key(call)
            val weight = 1

            val providerKey = ProviderKey(provider.name, key)
            val rateLimiterForCall = registry.computeIfAbsent(providerKey) {
                provider.rateLimiter()
            }

            call.attributes.put(
                GatewayRateLimitersForCallKey,
                call.attributes.getOrNull(GatewayRateLimitersForCallKey).orEmpty() + Pair(provider, rateLimiterForCall)
            )

            val state = rateLimiterForCall.tryConsume(weight)
            provider.modifyResponse(call, state)
            when (state) {
                is RateLimiter.State.Exhausted -> {
                    call.respond(HttpStatusCode.TooManyRequests)
                }

                is RateLimiter.State.Available -> {
                    if (rateLimiterForCall != RateLimiter.Unlimited) {
                        clearOnRefillJobs[providerKey]?.cancel()
                        clearOnRefillJobs[providerKey] = application.launch {
                            delay(state.refillAtTimeMillis - getTimeMillis())
                            registry.remove(providerKey, rateLimiterForCall)
                            clearOnRefillJobs.remove(providerKey)
                        }
                    }
                }
            }
        }
    }
}





