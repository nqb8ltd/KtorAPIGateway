package co.nqb8.queue

import co.nqb8.auth.GatewayJWTAuthForCallKey
import co.nqb8.auth.GatewayKeyAuthForCallKey
import co.nqb8.auth.getDataFromJwt
import co.nqb8.config.JwtPolicy
import co.nqb8.config.MessageQueue
import co.nqb8.config.Route
import co.nqb8.pipeline.Pipeline
import com.rabbitmq.client.AMQP
import com.rabbitmq.client.Connection
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.delay
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

class MessageQueuePipeline(
    private val connection: Connection,
    private val messageQueue: MessageQueue
): Pipeline {


    private val properties = AMQP.BasicProperties.Builder().apply { deliveryMode(2) }.build()


    override suspend fun pipe(call: RoutingCall, route: Route, body: JsonElement?) {
        println("Piping the line: $route")
        if (route.queue == null) return
        if (body == null){
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "missing body"))
            return
        }
        call.respond(HttpStatusCode.Created)
        if (messageQueue.transFormation == null){
            retry { sendMessage(route.queue, body) }
            return
        }




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

        val mapBody = body.jsonObject.toMutableMap().apply {
            putIfAbsent(messageQueue.transFormation.toKey, JsonPrimitive(sourceTransform))
        }
        println("Map: $mapBody")
        retry { sendMessage(route.queue, JsonObject(mapBody)) }
    }

    private fun sendMessage(queue: String, body: JsonElement): Result<Unit> {
        return runCatching {
            val channel = connection.createChannel().apply {
                confirmSelect()
                addConfirmListener(
                    { l, b -> println("Ack Tag: $l, multiple: $b")  },
                    { l, b -> println("Nack Tag: $l, multiple: $b")  }
                )
            }

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