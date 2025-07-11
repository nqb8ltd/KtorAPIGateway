package co.nqb8.forwarder

import io.ktor.client.*
import io.ktor.client.engine.apache5.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.readByteArray
import kotlinx.serialization.json.JsonElement

class Forwarder {

    private val client = HttpClient(Apache5) {
        install(ContentNegotiation) {
            json()
        }
        install(Logging) {
            this.level = LogLevel.ALL
            this.logger = object : Logger {
                override fun log(message: String) {
                    println("Request messages")
                    //println(message)
                }
            }
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
            addHeaders(heads, origin)
        }
        request
    }
    suspend fun routePart(
        path: String,
        heads: Headers,
        origin: String,
        form: Parameters? = null,
        multipart: MultiPartData? = null,
    ): HttpResponse = withContext(Dispatchers.IO) {
        val formRequest = when{
            form != null -> {
                client.submitForm(
                    url = path,
                    formParameters = form,
                    block = { addHeaders(heads, origin) }
                )
            }
            else -> {
                val partData = multipart.toPartData()
                client.submitFormWithBinaryData(
                    url = path,
                    formData = partData,
                    block = { addHeaders(heads, origin) }
                )
            }
        }
        formRequest
    }

    private suspend fun MultiPartData?.toPartData(): List<PartData> {
        val parts = mutableListOf<FormPart<*>>()
        this?.forEachPart { part ->
            val form = when(part){
                is PartData.BinaryChannelItem -> {
                    FormPart(part.name.orEmpty(), part.provider().readRemaining().readByteArray(), part.headers)
                }
                is PartData.BinaryItem -> {
                    FormPart(part.name.orEmpty(), part.provider().readByteArray(), part.headers)
                }
                is PartData.FileItem -> {
                    FormPart(part.name.orEmpty(), part.provider().readRemaining().readByteArray(), part.headers)
                }
                is PartData.FormItem -> {
                    FormPart(part.name.orEmpty(), part.value, part.headers)
                }
            }
            parts.add(form)
            part.dispose()
        }
        return formData(*parts.toTypedArray())
    }
    private fun  HttpRequestBuilder.addHeaders(headers: Headers, origin: String) {
        headers {
            append("X-Forwarded-For", origin)
            headers.forEach { key, value ->
                if (key != HttpHeaders.ContentLength) {
                    appendIfNameAbsent(key, value.joinToString(" "))
                }
            }
        }
    }
}