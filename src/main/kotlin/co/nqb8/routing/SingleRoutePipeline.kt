package co.nqb8.routing

import co.nqb8.config.Route
import co.nqb8.pipeline.Forwarder
import co.nqb8.pipeline.Pipeline
import io.ktor.client.statement.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.JsonElement

class SingleRoutePipeline(
    private val forwarder: Forwarder,
    private val baseUrl: String,
    private val method: Route.Method,
): Pipeline {

    override suspend fun pipe(call: RoutingCall,  route: Route) {
        val response = when(method.requestBodyType){
            Route.RequestBodyType.JSON -> {
                val json = runCatching { call.receive<JsonElement>() }.getOrNull()
                forwarder.route(
                    path = "$baseUrl${call.request.uri}",
                    methodType = method.method,
                    heads = call.request.headers,
                    body = json,
                    origin = call.request.origin.remoteAddress
                )
            }
            Route.RequestBodyType.FORM -> {
                val form = runCatching { call.receiveParameters() }.getOrNull()
                forwarder.routePart(
                    path = "$baseUrl${call.request.uri}",
                    heads = call.request.headers,
                    form = form,
                    origin = call.request.origin.remoteAddress
                )
            }
            Route.RequestBodyType.MULTIPART -> {
                val multipart = runCatching { call.receiveMultipart() }.getOrNull()
                forwarder.routePart(
                    path = "$baseUrl${call.request.uri}",
                    heads = call.request.headers,
                    multipart = multipart,
                    origin = call.request.origin.remoteAddress
                )
            }
        }
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

        call.response.status(response.status)
        call.respond(response.bodyAsChannel())
    }


}