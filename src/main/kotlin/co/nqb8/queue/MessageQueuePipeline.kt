package co.nqb8.queue

import co.nqb8.config.Route
import co.nqb8.pipeline.Pipeline
import com.rabbitmq.client.AMQP
import com.rabbitmq.client.Connection
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.delay
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

class MessageQueuePipeline(
    private val connection: Connection
): Pipeline {


    private val properties = AMQP.BasicProperties.Builder().apply { deliveryMode(2) }.build()


    override suspend fun pipe(call: RoutingCall, route: Route, body: JsonElement?) {
        if (route.queue == null) return
        if (body == null){
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "missing body"))
            return
        }
        call.respond(HttpStatusCode.Created)
        retry { sendMessage(route.queue, body) }

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