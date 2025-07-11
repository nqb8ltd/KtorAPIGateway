package co.nqb8.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import kotlinx.serialization.Serializable

@Serializable
data class Success<T>(
    val success: Boolean,
    val message: String? = null,
    val data: T? = null
)

@Serializable
data class Error(
    val success: Boolean = false,
    val message: String
)

suspend inline fun <reified T> ApplicationCall.respondSuccess(data: T? = null, message: String? = null, status: HttpStatusCode = HttpStatusCode.OK) {
    respond(status, Success(success = true, data = data, message = message))
}

suspend fun ApplicationCall.respondError(message: String, status: HttpStatusCode = HttpStatusCode.BadRequest) {
    respond(status, Error(message = message))
}