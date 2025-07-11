package co.nqb8.pipeline

import co.nqb8.auth.GatewayJWTAuthForCallKey
import co.nqb8.auth.GatewayKeyAuthForCallKey
import co.nqb8.auth.getDataFromJwt
import co.nqb8.config.JwtPolicy
import co.nqb8.config.MessageQueue
import co.nqb8.config.Route
import co.nqb8.data.requestRepository
import com.rabbitmq.client.AMQP
import com.rabbitmq.client.Connection
import io.ktor.http.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

class MessageQueuePipeline(
    connection: Connection,
    private val messageQueue: MessageQueue
): Pipeline {
    private val properties = AMQP.BasicProperties.Builder().apply { deliveryMode(2) }.build()
    private val channel = connection.createChannel().apply {
        confirmSelect()
        addConfirmListener(
            { l, b -> println("Ack Tag: $l, multiple: $b")  },
            { l, b -> println("Nack Tag: $l, multiple: $b")  }
        )
    }


    override suspend fun pipe(call: RoutingCall, route: Route) {
        if (route.queue == null) return
        val body = runCatching { call.receive<JsonElement>() }.getOrNull()
        call.application.requestRepository.update(call.callId){
            it.requestBody = body.toString()
            it.upstreamUrl = "queue://${route.queue}"
        }
        if (body == null){
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "missing body"))
            return
        }
        val response = mapOf("status" to "success", "message" to "Package registered successfully")
        call.application.requestRepository.update(call.callId){
            it.responseBody = Json.encodeToString(response)
        }
        call.respond(HttpStatusCode.Created, response)
        if (messageQueue.transFormation == null){
            withContext(Dispatchers.IO) {
                retry { sendMessage(route.queue, body) }
            }
            return
        }

        transformAndSendMessage(call, route, body)
    }

    private suspend fun transformAndSendMessage(call: RoutingCall, route: Route, body: JsonElement) = withContext(Dispatchers.IO) {
        if (messageQueue.transFormation == null) return@withContext
        if (route.queue == null) return@withContext
        val sourceTransform: String = when(messageQueue.transFormation.transformSource){
            MessageQueue.TransformSource.PATH -> {
                val callUrl = Url(call.request.uri).segments
                val routeUrl = Url(route.uri).segments
                val pathIndex = routeUrl.indexOf(messageQueue.transFormation.fromKey)
                if (pathIndex != -1 && callUrl.size == routeUrl.size)callUrl[pathIndex]
                else ""
            }
            MessageQueue.TransformSource.HEADER -> call.request.header(messageQueue.transFormation.fromKey).orEmpty()
            MessageQueue.TransformSource.KEY -> {
                val jwt = call.attributes.getOrNull(GatewayJWTAuthForCallKey)
                val key = call.attributes.getOrNull(GatewayKeyAuthForCallKey)
                var data = ""
                if (jwt != null){
                    val dataKey = (route.authenticationPolicy as JwtPolicy).data
                    data = getDataFromJwt(messageQueue.transFormation.fromKey, jwt.getClaim(dataKey).asMap())
                }
                if (key != null){
                    data = getDataFromJwt(messageQueue.transFormation.fromKey, key.toMutableMap())
                }
                data
            }
        }

        var mapBody: Map<String, JsonElement> = body.jsonObject.toMutableMap().apply {
            putIfAbsent(messageQueue.transFormation.toKey, JsonPrimitive(sourceTransform))
        }
        if (messageQueue.transFormation.dataBody != null){
            val bodyValues = messageQueue.transFormation.dataBody.split(",")
            mapBody = buildMap{
                bodyValues.forEach { dataBody ->
                    val (key, value) = dataBody.split("=")
                    if (value != "body"){
                        put(key, JsonPrimitive(value))
                    }else{
                        put(key, JsonObject(mapBody))
                    }
                }
            }
        }

        println("Map: $mapBody")
        retry { sendMessage(route.queue, JsonObject(mapBody)) }
    }

    private fun sendMessage(queue: String, body: JsonElement): Result<Unit> {
        return runCatching {
            channel.queueDeclare(queue, true, false, false, null)
            val data = Json.encodeToString(body)
            channel.basicPublish("", queue, properties, data.encodeToByteArray())
        }
    }

    private suspend fun <T> retry(
        times: Int = 0, //if zero it retries multiple times
        initialDelay: Long = 3000, // milliseconds
        maxDelay: Long = 5000, // milliseconds
        backoffMultiplier: Double = 2.0,
        block: suspend () -> Result<T>
    ): Result<T> {
        var attempt = 0
        var delay = initialDelay
        while (attempt < times || times == 0) {
            val request = block()
            if (request.isSuccess) return request
            else{
                attempt++
                delay = (delay * backoffMultiplier).toLong().coerceIn(initialDelay, maxDelay)
                delay(delay)
            }
        }
        return Result.failure(Throwable("Internet issue occurred"))
    }



}