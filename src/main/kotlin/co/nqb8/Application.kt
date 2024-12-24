package co.nqb8

import co.nqb8.plugins.configurePlugins
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import kotlinx.serialization.json.Json

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configurePlugins()
    val client = HttpClient(CIO){
        install(Logging){
            level = LogLevel.NONE
            logger = Logger.SIMPLE
        }
        install(ContentNegotiation) {
            json()
        }
        expectSuccess = false
    }
    val json = Json
    registerServices(client, json)
}
