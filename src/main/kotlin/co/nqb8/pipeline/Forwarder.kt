package co.nqb8.pipeline

import io.ktor.client.*
import io.ktor.client.engine.apache5.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement

class Forwarder {

    private val client = HttpClient(Apache5) {
        install(ContentNegotiation) {
            json()
        }
        expectSuccess = false
        engine {
            followRedirects = false
            socketTimeout = 10_000
            connectTimeout = 10_000
            connectionRequestTimeout = 20_000
        }
    }

    suspend fun route(
        path: String,
        methodType: String,
        heads: Headers,
        origin: String,
        body: JsonElement? = null
    ): HttpResponse = withContext(Dispatchers.IO) {
        val request = client.request(path) {
            method = HttpMethod.parse(methodType)
            if (body != null) {
                setBody(body)
            }
            headers {
                append("X-Forwarded-For", origin)
                heads.forEach { key, value ->
                    if (key != HttpHeaders.ContentLength) {
                        appendIfNameAbsent(key, value.joinToString(" "))
                    }
                }
            }
        }
        request
    }
}