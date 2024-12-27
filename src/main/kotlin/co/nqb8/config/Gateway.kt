package co.nqb8.config

import co.nqb8.aggregate.AggregatePipeline
import co.nqb8.auth.AuthProviders
import co.nqb8.pipeline.Forwarder
import co.nqb8.pipeline.Pipeline
import co.nqb8.queue.MessageQueuePipeline
import co.nqb8.ratelimiter.RateLimitProvide
import co.nqb8.ratelimiter.providers
import co.nqb8.routing.SingleRoutePipeline
import com.rabbitmq.client.ConnectionFactory
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import kotlinx.serialization.json.JsonElement
import kotlin.time.Duration.Companion.seconds


var servicesRouting: Routing? = null

fun Application.configureGateway(services: List<Service>?, forwarder: Forwarder) {

    services?.let {
        servicesRouting = routing {
            services.forEach { service ->
                registerEndpoints(
                    service = service,
                    forwarder = forwarder,
                    registeredRoutes = emptyMap()
                )
            }
        }
    }

    val getRegisteredRoutes = { (servicesRouting as RoutingRoot?)?.getRoutesRegistered().orEmpty() }

    gatewayApis(
        registerEndpoints = { service -> registerEndpoints(service, forwarder, getRegisteredRoutes()) }
    )
}



private fun Routing.registerEndpoints(
    service: Service,
    forwarder: Forwarder,
    registeredRoutes: Map<String, MutableList<String>>
) {
    service.aggregates.forEach { aggregate ->
        if (!registeredRoutes.containsKey(aggregate.uri)) {
            registerAggregates(service, aggregate, forwarder)
        }
    }
    val messageQueuePipeline = createAmpqConnection(service)
    service.routes.forEach { route ->
        if (!hasRegisteredRoute(route, registeredRoutes)){
            registerRoutes(route, forwarder, service, messageQueuePipeline)
        }
    }
}

private fun Routing.registerAggregates(
    service: Service,
    aggregate: Aggregate,
    forwarder: Forwarder
) {
    setUpRateLimit(aggregate.rateLimitPolicy, aggregate.uri)
    setUpAuthentication(aggregate.authenticationPolicy, aggregate.uri)
    val aggregatePipeline = AggregatePipeline(forwarder = forwarder, service = service, aggregate = aggregate)
    registerRouteByMethods(
        uri = aggregate.uri,
        method = "GET",
        pipelines = listOf(aggregatePipeline),
        route = Route(uri = aggregate.uri)
    )
}

private fun Routing.registerRoutes(
    route: Route,
    forwarder: Forwarder,
    service: Service,
    messageQueuePipeline: MessageQueuePipeline? = null
) {
    setUpRateLimit(route.rateLimitPolicy, route.uri)
    route.methods?.forEach { method ->
        val pipelines = buildList {
            setUpAuthentication(route.authenticationPolicy, route.uri)
            if (messageQueuePipeline != null) {
                println("Adding queues")
                add(messageQueuePipeline)
            }
            add(SingleRoutePipeline(forwarder = forwarder, baseUrl = service.baseUrl, method = method))
        }
        registerRouteByMethods(
            uri = route.uri,
            method = method,
            pipelines = pipelines,
            route = route
        )
    }
}

private fun setUpRateLimit(rateLimitPolicy: RateLimitPolicy?, uri: String){
    val rate = rateLimitPolicy ?: RateLimitPolicy(limit = 10, refreshTimeSeconds = 0)
    val rateLimitProvide = RateLimitProvide(
        name = uri,
        limit = rate.limit,
        refillPeriod = rate.refreshTimeSeconds.seconds,
        key = {  call ->
            when(rate.requestKey){
                RateLimitPolicy.RequestKey.IP -> call.request.origin.remoteAddress
                RateLimitPolicy.RequestKey.API_KEY -> {
                    call.request.header(rate.header) ?: call.request.origin.remoteAddress
                }
                RateLimitPolicy.RequestKey.BEARER -> {
                    val bearer = call.request.header(HttpHeaders.Authorization)
                    bearer?.replace(" ","-") ?: call.request.origin.remoteAddress
                }
            }
        }
    )
    providers.add(rateLimitProvide)
}
private fun setUpAuthentication(authenticationPolicy: AuthenticationPolicy?, uri: String){
    if (authenticationPolicy == null) return
    AuthProviders.putIfAbsent(uri, authenticationPolicy)
}

private fun Routing.registerRouteByMethods(
    route: Route,
    uri: String,
    method: String,
    pipelines: List<Pipeline>
) {
    route(uri, method = HttpMethod(method)){
        handle {
            val body = runCatching { call.receive<JsonElement>() }.getOrNull()
            pipelines.forEach { pipeline ->
                if (!call.isHandled) {
                    pipeline.pipe(call, route, body)
                }
            }
            if (!call.isHandled) {
                call.respond(HttpStatusCode.InternalServerError)
            }
        }
    }

}

private fun Routing.createAmpqConnection(service: Service): MessageQueuePipeline?{
    if (service.messageQueue == null) return null
    val factoryConnection = runCatching {
        ConnectionFactory().apply {
            host = service.messageQueue.host
            port = service.messageQueue.port
            username = service.messageQueue.username ?: "guest"
            password = service.messageQueue.password ?: "guest"
        }.newConnection()
    }.getOrNull()

    if (factoryConnection == null) return null

    val connection = application.attributes.computeIfAbsent(AttributeKey(service.messageQueue.getAddress())){ factoryConnection }
    return application.attributes.computeIfAbsent(AttributeKey(service.name)){
        println("Calculating message queue for ${service.name}")
        MessageQueuePipeline(connection, service.messageQueue)
    }
}


private fun hasRegisteredRoute(route: Route, registeredRoutes: Map<String, MutableList<String>>): Boolean{
    if (!registeredRoutes.containsKey(route.uri)) return false
    val routeRegistered = registeredRoutes[route.uri]
    if (route.methods != null && routeRegistered != null){
        routeRegistered.forEach {
            if (route.methods.contains(it)) return true
        }
    }
    return false
}

private fun RoutingRoot.getRoutesRegistered(): Map<String, MutableList<String>> {
    return buildMap{
        getAllRoutes().forEach { route ->
            val cleanRoute = route.toString().replace("method:","").replace(")","")
            val (path, method) = cleanRoute.split("/(")
            if (path.contains('_')) return@forEach
            if (containsKey(path)){
                val value = get(path)!!.apply {
                    add(method)
                }
                put(path, value)
            }else{
                put(path, mutableListOf(method))
            }

        }
    }
}