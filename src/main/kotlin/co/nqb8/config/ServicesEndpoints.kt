package co.nqb8.config

import co.nqb8.auth.GatewayAuthApiKeyRegistryKey
import co.nqb8.dashboard.DashboardUseCase
import co.nqb8.data.RequestRepository
import co.nqb8.forwarder.Forwarder
import co.nqb8.gateway.KateRoutes
import co.nqb8.gateway.routes
import co.nqb8.gateway.startGateway
import co.nqb8.plugins.json
import co.nqb8.plugins.respondSuccess
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import kotlin.io.path.Path
import kotlin.io.path.createFile
import kotlin.io.path.deleteExisting
import kotlin.io.path.exists
import kotlin.io.path.writeText

class ServiceException(message: String) : Exception(message)
private const val SERVICE_FILE = "../data/services.json"

fun Application.servicesApi(forwarder: Forwarder) {
    routing {
        authenticate("gateway-auth") {
            get("/_services") {
                call.respondSuccess(message = "Service fetched successfully", data = currentServices())
            }
            post("/_services") {
                val newServices = call.receive<List<Service>>()
                val result = findAndUpdateOrCreateService(newServices)
                call.respondSuccess(message = "Service updated successfully", data = result)
                startGateway(newServices, forwarder)
            }
            delete("/_services") {
                Path(SERVICE_FILE).deleteExisting()
                call.respondSuccess("Service deleted successfully")
            }
            get("/_routes"){
                val mappedRoutes = routes.map {
                    KateRoutes(
                        path = it.uri,
                        method = it.method,
                        route = it.route,
                    )
                }
                call.respondSuccess(message = "Routes fetched successfully", data = mappedRoutes)
            }

            post("/_invalidate/{key}"){
                val key = call.parameters["key"]
                val keys = call.application.attributes[GatewayAuthApiKeyRegistryKey]
                keys.remove(key)
                call.respond(HttpStatusCode.OK)
            }
        }
        startGateway(currentServices(), forwarder)
        dashboardApis(
            dashboardUseCase = DashboardUseCase(RequestRepository(this.application)),
            forwarder = forwarder
        )
    }
}

internal fun findAndUpdateOrCreateService(services: List<Service>): List<Service> {
    val updatesByName: Map<String, Service> = services.associateBy { it.name }
    val result = currentServices().map { current ->
        updatesByName[current.name]?.let { update ->
            current.copy(
                routes     = current.routes + update.routes,
                aggregates = current.aggregates + update.aggregates
            )
        } ?: current
    }
    val existingNames = currentServices().map { it.name }.toSet()
    val newOnes = result + services.filter { it.name !in existingNames }
    writeCurrentServices(newOnes)
    return newOnes
}

internal fun findAndUpdateOrCreateRoute(serviceToUpdate: Service): List<Service> {
    val services =  currentServices().map { currentService ->
        if (currentService.name == serviceToUpdate.name) {
            // Update routes in this matching service
            val updatedRoutes = serviceToUpdate.routes.fold(currentService.routes) { acc, incomingRoute ->
                // Try to find an existing route by some unique field, e.g. `path`
                val existingIndex = acc.indexOfFirst { it.uri == incomingRoute.uri && it.methods?.containsAll(incomingRoute.methods.orEmpty()) == true }
                if (existingIndex != -1) {
                    // Replace or merge the existing route
                    acc.toMutableList().apply { this[existingIndex] = incomingRoute }
                } else {
                    // Add as new
                    acc + incomingRoute
                }
            }
            currentService.copy(routes = updatedRoutes)
        } else {
            // Not the service we’re updating — leave untouched
            currentService
        }
    }.let { updatedServices ->
        // If the service didn’t exist at all, add it
        val serviceExists = updatedServices.any { it.name == serviceToUpdate.name }
        if (serviceExists) updatedServices else updatedServices + serviceToUpdate
    }
    writeCurrentServices(services)
    return services
}


internal fun currentServices(): List<Service> {
    val path = Path(SERVICE_FILE)
    if (!path.exists()) {
        path.createFile()
        path.writeText("[]")
    }
    return path.toFile().readText().let {
        json.decodeFromString<List<Service>>(it)
    }
}

private fun writeCurrentServices(services: List<Service>){
    val path = Path(SERVICE_FILE).toFile()
    path.writeText(Json.encodeToString(services))
}