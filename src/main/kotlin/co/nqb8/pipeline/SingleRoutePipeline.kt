package co.nqb8.pipeline

import co.nqb8.config.Route
import co.nqb8.data.isUtf8Compatible
import co.nqb8.data.requestRepository
import co.nqb8.forwarder.Forwarder
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.util.date.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

class SingleRoutePipeline(
    private val forwarder: Forwarder,
    private val baseUrl: String,
    private val method: Route.Method,
): Pipeline {

    override suspend fun pipe(call: RoutingCall,  route: Route) {
        val forwarded = when(method.requestBodyType){
            Route.RequestBodyType.JSON -> {
                val json = runCatching { call.receive<JsonElement>() }.getOrNull()
                call.application.requestRepository.update(call.callId){
                    it.requestBody = Json.encodeToString(json)
                    it.upstreamUrl = "$baseUrl${call.request.uri}"
                }
                kotlin.runCatching {
                    forwarder.route(
                        path = "$baseUrl${call.request.uri}",
                        methodType = method.method,
                        heads = call.request.headers,
                        body = json,
                        origin = call.request.headers["X-Forwarded-For"] ?: call.request.origin.remoteAddress
                    )
                }
            }
            Route.RequestBodyType.FORM -> {
                val form = runCatching { call.receiveParameters() }.getOrNull()
                call.application.requestRepository.update(call.callId){
                    it.requestBody = Json.encodeToString(form?.toMap())
                    it.upstreamUrl = "$baseUrl${call.request.uri}"
                }
                kotlin.runCatching {
                    forwarder.routePart(
                        path = "$baseUrl${call.request.uri}",
                        heads = call.request.headers,
                        form = form,
                        origin = call.request.headers["X-Forwarded-For"] ?: call.request.origin.remoteAddress
                    )
                }
            }
            Route.RequestBodyType.MULTIPART -> {
                val multipart = runCatching { call.receiveMultipart() }.getOrNull()
                call.application.requestRepository.update(call.callId){
                    it.requestBody = "multipart"
                    it.upstreamUrl = "$baseUrl${call.request.uri}"
                }
                runCatching {
                    forwarder.routePart(
                        path = "$baseUrl${call.request.uri}",
                        heads = call.request.headers,
                        multipart = multipart,
                        origin = call.request.headers["X-Forwarded-For"] ?: call.request.origin.remoteAddress
                    )
                }

            }
        }
        forwarded.onSuccess { response ->
            call.response.headers.apply {
                response.headers.forEach { key, value ->
                    if (key.startsWith('X')) {
                        append(key, value.joinToString(" "))
                    }
                    if (key.startsWith("Content")){
                        append(key, value.joinToString(" "))
                    }
                }
            }
            val textBody = response.bodyAsText()
            call.application.requestRepository.update(call.callId){
                it.upstreamUrl = "$baseUrl${call.request.uri}"
                if (textBody.isUtf8Compatible()) {
                    it.responseBody = textBody
                }
                val requestTime = response.requestTime.toJvmDate().toInstant().toEpochMilli()
                val responseTime = response.responseTime.toJvmDate().toInstant().toEpochMilli()
                it.responseTime = responseTime - requestTime
                it.responseHeaders = Json.encodeToString(response.headers.toMap())
                it.responseStatusCode = response.status.value
            }
            call.response.status(response.status)
            call.respond(response.bodyAsChannel())
        }.onFailure { error ->
            call.application.requestRepository.update(call.callId){
                it.responseStatusCode = 500
                it.responseBody = error.message
            }
            call.respond(HttpStatusCode.InternalServerError)
        }
    }


}