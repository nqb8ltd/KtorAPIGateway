package co.nqb8.config

import co.nqb8.auth.GatewayAuthApiKeyRegistryKey
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*


fun Application.gatewayApis(
    getRegisteredRoutes: () -> Map<String, MutableList<String>>,
    registerEndpoints: Routing.(Service) -> Unit,
) {
    routing {
        get("/_services") {
            call.respond(HttpStatusCode.OK, getRegisteredRoutes())
        }
        put("/_services") {
            if (servicesRouting == null) return@put call.respond(HttpStatusCode.BadRequest, "No services registered")
            val updatedServices = call.receive<List<Service>>()
            updatedServices.forEach { service ->
                registerEndpoints(service)
            }
            call.respond(HttpStatusCode.Created, "Services updated")

        }
        post("/_services") {
            if (servicesRouting != null) return@post call.respond(
                HttpStatusCode.BadRequest,
                mapOf("warning" to "Service already exists. Kindly update")
            )
            val newServices = call.receive<List<Service>>()

            servicesRouting = routing {
                newServices.forEach { service ->
                    registerEndpoints(service)
                }
            }
            call.respond(HttpStatusCode.Created)
        }

        post("/_invalidate/{key}"){
            val key = call.parameters["key"]
            val keys = call.application.attributes[GatewayAuthApiKeyRegistryKey]
            keys.remove(key)
            call.respond(HttpStatusCode.OK)
        }
    }
}