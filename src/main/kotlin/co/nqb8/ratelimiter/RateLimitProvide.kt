package co.nqb8.ratelimiter

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.response.*
import io.ktor.util.date.*
import kotlin.time.Duration

data class RateLimitProvide(
    val name: String,
    val limit: Int,
    val refillPeriod: Duration,
    val key: (ApplicationCall) -> String = { call -> call.request.origin.remoteAddress },
){
    var rateLimiter: () -> RateLimiter = { RateLimiter.default(limit = limit, refillPeriod = refillPeriod, clock = { getTimeMillis() }) }
    var modifyResponse: (ApplicationCall, RateLimiter.State) -> Unit = { call, state ->
        when (state) {
            is RateLimiter.State.Available -> {
                call.response.headers.appendIfAbsent("X-RateLimit-Limit", state.limit.toString())
                call.response.headers.appendIfAbsent("X-RateLimit-Remaining", state.remainingTokens.toString())
                call.response.headers.appendIfAbsent("X-RateLimit-Reset", (state.refillAtTimeMillis / 1000).toString())
            }

            is RateLimiter.State.Exhausted -> {
                if (!call.response.headers.contains(HttpHeaders.RetryAfter)) {
                    call.response.header(HttpHeaders.RetryAfter, state.toWait.inWholeSeconds.toString())
                }
            }
        }
    }
}