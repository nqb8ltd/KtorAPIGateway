package co.nqb8.config

import co.nqb8.auth.GatewayAuthApiKeyRegistryKey
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.io.path.Path


fun Application.gatewayApis(
    registerEndpoints: Routing.(Service) -> Unit,
) {
    routing {
        authenticate("gateway-auth") {
            get("/_services") {
                val services = Path("../data/services.json").toFile().readText().let {
                    Json.decodeFromString<List<Service>>(it)
                }
                call.respond(HttpStatusCode.OK, services)
            }
            post("/_services") {
                val newServices = call.receive<List<Service>>()
                val services = Path("../data/services.json").toFile().readText().let {
                    Json.decodeFromString<List<Service>>(it)
                }
                updateServices(services, newServices)

                servicesRouting = routing {
                    newServices.forEach { service ->
                        registerEndpoints(service)
                    }
                }
                call.respond(HttpStatusCode.Created)
            }
            delete("/_services") {
                val services = Path("../data/services.json").toFile()
                services.delete()
                call.respond(HttpStatusCode.Created)
            }
        }


        post("/_invalidate/{key}"){
            val key = call.parameters["key"]
            val keys = call.application.attributes[GatewayAuthApiKeyRegistryKey]
            keys.remove(key)
            call.respond(HttpStatusCode.OK)
        }
    }
}

internal fun updateServices(old: List<Service>, new: List<Service>) {
    val updatedServices = mutableSetOf<Service>()
    new.forEach { service ->
        val oldService = old.find { it.name == service.name }
        if (oldService != null) {
            val routes = oldService.routes.toMutableList().apply { addAll(service.routes) }.toSet().toList()
            val aggregates = oldService.aggregates.toMutableList().apply { addAll(service.aggregates) }.toSet().toList()
            val updated = oldService.copy(
                routes = routes,
                aggregates = aggregates,
                messageQueue = if (service.messageQueue != oldService.messageQueue) service.messageQueue else oldService.messageQueue,
                baseUrl = if (service.baseUrl != oldService.baseUrl) service.baseUrl else oldService.baseUrl
            )
            updatedServices.add(updated)
        }else{
            updatedServices.add(service)
        }
    }
    if (updatedServices.isEmpty()){
        updatedServices.addAll(new)
    }
    val path = Path("../data/services.json").toFile()
    path.writeText(Json.encodeToString(updatedServices))
}