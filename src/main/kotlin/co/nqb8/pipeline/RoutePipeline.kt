package co.nqb8.pipeline

import co.nqb8.config.Route
import io.ktor.server.routing.*
import kotlinx.serialization.json.JsonElement

interface Pipeline {

    /**
     * Apply changes to the request chain and respond if needed
     * @param call
     * @param route
     * @param body
     * @return if the pipeline has responded to the client
     */
    suspend fun pipe(call: RoutingCall, route: Route, body: JsonElement? = null)
}


