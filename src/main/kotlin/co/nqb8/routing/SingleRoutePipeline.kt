package co.nqb8.routing

import co.nqb8.config.Route
import co.nqb8.pipeline.Forwarder
import co.nqb8.pipeline.Pipeline
import io.ktor.client.call.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.JsonElement

class SingleRoutePipeline(
    private val forwarder: Forwarder,
    private val baseUrl: String,
    private val method: String,
): Pipeline {

    override suspend fun pipe(call: RoutingCall,  route: Route, body: JsonElement?) {
        val response = forwarder.route(
            path = "$baseUrl${call.request.uri}",
            methodType = method,
            heads = call.request.headers,
            body = body,
            origin = call.request.origin.remoteAddress
        )
        call.response.headers.apply {
            response.headers.forEach { key, value ->
                if (key.startsWith('X')) {
                    append(key, value.joinToString(" "))
                }
            }
        }
        val responseBody = response.body<JsonElement>()
        call.respond(response.status, responseBody)
    }
}