package co.nqb8

import co.nqb8.config.Service
import co.nqb8.config.configureGateway
import co.nqb8.pipeline.Forwarder
import io.ktor.server.application.*
import io.ktor.util.*
import kotlinx.serialization.json.Json
import kotlin.io.path.Path
import kotlin.io.path.createFile
import kotlin.io.path.exists
import kotlin.io.path.writeText


fun Application.registerServices() {
    val forwarder = Forwarder()
    attributes.put(AttributeKey("forwarder"), forwarder)
    val servicesFile = Path("../data/services.json")
    if (!servicesFile.exists()) {
        servicesFile.createFile()
        servicesFile.writeText("[]")
    }
   val services = runCatching {
        val config = servicesFile.toFile().readText()
        Json.decodeFromString<List<Service>>(config)
    }.getOrNull()
    configureGateway(services, forwarder)
}








