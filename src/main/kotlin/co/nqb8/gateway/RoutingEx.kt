package co.nqb8.gateway

import co.nqb8.config.Route
import co.nqb8.pipeline.Pipeline
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

data class KateRouting(
    val routing: Routing,
    val route: Route,
    val uri: String,
    val method: String,
    val pipelines: List<Pipeline>
)

@Serializable
data class KateRoutes(
    val path: String,
    val method: String,
    val route: Route
)