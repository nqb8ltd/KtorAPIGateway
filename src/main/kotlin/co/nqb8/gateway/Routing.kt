package co.nqb8.gateway

import co.nqb8.auth.AuthProviders
import co.nqb8.config.*
import co.nqb8.config.Route
import co.nqb8.forwarder.Forwarder
import co.nqb8.pipeline.AggregatePipeline
import co.nqb8.pipeline.MessageQueuePipeline
import co.nqb8.pipeline.SingleRoutePipeline
import co.nqb8.ratelimiter.RateLimitProvide
import co.nqb8.ratelimiter.providers
import com.rabbitmq.client.ConnectionFactory
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import kotlin.properties.Delegates
import kotlin.time.Duration.Companion.seconds


internal var routes: List<KateRouting> by Delegates.observable(listOf()){ _, oldValue, newValue ->
    val difference = newValue.filterNot(oldValue::contains)
    difference.forEach {
        it.routing.route(it.uri, HttpMethod(it.method)) {
            handle {
                it.pipelines.forEach { pipeline ->
                    if (!call.isHandled) {
                        pipeline.pipe(call, it.route)
                    }
                }
                if (!call.isHandled) {
                    call.respond(HttpStatusCode.InternalServerError)
                }
            }
        }
    }
}

fun Routing.startGateway(services: List<Service>, forwarder: Forwarder){
    println("Services: ${services.size}")
    services.forEach {
        registerUpStreams(it, forwarder)
    }
}

private fun Routing.registerUpStreams(
    service: Service,
    forwarder: Forwarder
) {
    service.aggregates.forEach { aggregate ->
        if (routes.none { it.uri == aggregate.uri }){
            registerAggregates(service, aggregate, forwarder)
        }
    }
    val messageQueuePipeline = createAmpqConnection(service)
    service.routes.forEach { route ->
        registerRoutes(route, forwarder, service, messageQueuePipeline)
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
    routes = routes + KateRouting(
        routing = this,
        route = Route(uri = aggregate.uri, authenticationPolicy = aggregate.authenticationPolicy, rateLimitPolicy = aggregate.rateLimitPolicy),
        uri = aggregate.uri,
        method = "GET",
        pipelines = listOf(aggregatePipeline)
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
                println("Adding queues: ${route.uri}: $method")
                add(messageQueuePipeline)
            }
            add(SingleRoutePipeline(forwarder = forwarder, baseUrl = service.baseUrl, method = method))
        }
        if (routes.none { it.uri == route.uri && it.method == method.method }){
            routes = routes + KateRouting(
                routing = this,
                route = route,
                uri = route.uri,
                method = method.method,
                pipelines = pipelines
            )
        }
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