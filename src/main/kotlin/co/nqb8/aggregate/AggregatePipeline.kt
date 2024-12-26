package co.nqb8.aggregate

import co.nqb8.config.Aggregate
import co.nqb8.config.Route
import co.nqb8.config.Service
import co.nqb8.pipeline.Forwarder
import co.nqb8.pipeline.Pipeline
import co.nqb8.utils.buildChildRoute
import io.ktor.client.call.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.JsonElement

class AggregatePipeline(
    private val forwarder: Forwarder,
    private val service: Service,
    private val aggregate: Aggregate
): Pipeline {

    override suspend fun pipe(call: RoutingCall,  route: Route, body: JsonElement?) {
        coroutineScope {
            val routes = call.aggregateRequests(aggregate.routes, this, call.request.headers)
            val results = routes.awaitAll()
            if (results.any { !it.status.isSuccess() }){
                val result = results.first { !it.status.isSuccess() }
                call.respond(result.status, result.body<JsonElement>())
            }
            val headers = mutableMapOf<String, List<String>>()
            val response: Map<String, JsonElement> = aggregateResponseAndHeaders(results, aggregate.routes, headers)
            call.response.headers.apply {
                headers.forEach { (key, value) ->
                    append(key, value.joinToString(" "))
                }
            }
            call.respond(
                if (response.isEmpty()) HttpStatusCode.InternalServerError else HttpStatusCode.OK,
                response
            )
        }
    }

    private fun RoutingCall.aggregateRequests(
        routes: List<Route>,
        coroutineScope: CoroutineScope,
        heads: Headers
    ): List<Deferred<HttpResponse>> {
        val requests = routes.map { route ->
            val fullPath = buildChildRoute(
                parentUrl = Url(aggregate.uri),
                childUrl = Url(route.uri),
                callUrl = Url(request.uri),
            )
            val childBaseUrl = route.baseUrl ?: service.baseUrl
            coroutineScope.route(url = "$childBaseUrl/$fullPath", headers = heads, origin = request.origin.remoteAddress)
        }
        return requests
    }


    private suspend fun aggregateResponseAndHeaders(
        results: List<HttpResponse>,
        routes: List<Route>,
        headers: MutableMap<String, List<String>>
    ): Map<String, JsonElement> {
        return buildMap {
            results.forEachIndexed { index, response ->
                if (response.status.isSuccess()) {
                    val responseBody = response.body<JsonElement>()
                    put(routes[index].tag.orEmpty(), responseBody)
                    response.headers.forEach { key, value ->
                        headers[key] = value
                    }
                }
            }
        }
    }



    private fun CoroutineScope.route(url: String, headers: Headers, origin: String): Deferred<HttpResponse>{
        return async {
            forwarder.route(
                path = url,
                methodType = "GET",
                heads = headers,
                origin = origin
            )
        }
    }



}