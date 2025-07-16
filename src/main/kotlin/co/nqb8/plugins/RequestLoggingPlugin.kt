package co.nqb8.plugins

import co.nqb8.data.RequestRepository
import co.nqb8.data.requestRepository
import io.ktor.server.application.*
import io.ktor.server.application.hooks.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.request.*
import io.ktor.util.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json

val RequestLogging = createApplicationPlugin(name = "RequestLoggingPlugin") {
    val requestRepository = application.requestRepository
    onCall { call ->
        if (call.request.uri.startsWith("/_")) return@onCall
        call.attributes.put(AttributeKey("start-time"), Clock.System.now())
        //save the request
        val requestId = call.callId ?: return@onCall
        val clientIp = call.request.headers["X-Forwarded-For"] ?: call.request.origin.remoteHost
        val httpMethod = call.request.httpMethod.value
        val path = call.request.uri
        val requestHeaders = Json.encodeToString(call.request.headers.toMap())
        val queryParams = Json.encodeToString(call.request.rawQueryParameters.toMap())
        requestRepository.createRequestLog(requestId, clientIp, httpMethod, path, requestHeaders, queryParams)

    }
    on(ResponseSent){ call ->
        if (call.request.uri.startsWith("/_")) return@on
        updateEnd(call, requestRepository)
    }
    on(CallFailed){ call, exception ->
        if (call.request.uri.startsWith("/_")) return@on
        updateEnd(call, requestRepository, exception)
    }
    onCallRespond { call ->
        if (call.request.uri.startsWith("/_")) return@onCallRespond
        updateEnd(call, requestRepository)
    }
}

private fun updateEnd(call: ApplicationCall, requestRepository: RequestRepository, exception: Throwable? = null) {
    val endTime = Clock.System.now().toEpochMilliseconds()
    val startTime = call.attributes.get<Instant>(AttributeKey("start-time")).toEpochMilliseconds()
    requestRepository.update(call.callId){
        it.endTimestamp = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        it.latencyMs = endTime - startTime
        it.responseStatusCode = call.response.status()?.value
        it.responseBody = exception?.message
    }
}