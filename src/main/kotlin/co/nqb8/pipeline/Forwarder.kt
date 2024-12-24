package co.nqb8.pipeline

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.serialization.json.JsonElement

class Forwarder (private val client: HttpClient) {

    suspend fun route(
        path: String,
        methodType: String,
        heads: Headers,
        body: JsonElement? = null
    ): HttpResponse {
        val request = client.request(path){
            method = HttpMethod.parse(methodType)
            if (body != null) {
                setBody(body)
            }
            headers {
                heads.forEach { key, value ->
                    if (key != HttpHeaders.ContentLength) {
                        appendIfNameAbsent(key, value.joinToString(" "))
                    }
                }
            }
        }
        return request
    }
}