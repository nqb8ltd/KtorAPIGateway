package co.nqb8.config

import co.nqb8.dashboard.DashboardUseCase
import co.nqb8.dashboard.dto.Login
import co.nqb8.forwarder.Forwarder
import co.nqb8.gateway.KateRoutes
import co.nqb8.gateway.routes
import co.nqb8.gateway.startGateway
import co.nqb8.plugins.respondError
import co.nqb8.plugins.respondSuccess
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.util.collections.*

val adminTokens = ConcurrentSet<String>()

fun Routing.dashboardApis(dashboardUseCase: DashboardUseCase, forwarder: Forwarder){
    post("/_login"){
        val user = environment.config.property("admin.email").getString()
        val password = environment.config.property("admin.password").getString()
        val credentials = call.receive<Login>()
        if (user != credentials.email && password != credentials.password){
            return@post call.respondError("Invalid credentials")
        }
        val token = generateRandomToken()
        adminTokens.add(token)
        call.respondSuccess(data = mapOf("token" to token))
    }
    authenticate("admin-auth") {
        //home
        route("/_dashboard"){
            get("/home"){
                val dashboardHome = dashboardUseCase.getHomeStatistics()
                call.respondSuccess(message = "Statistics fetched successfully", data = dashboardHome)
            }
            get("/traces"){
                val page = call.queryParameters["page"]?.toIntOrNull() ?: 0
                val count = call.queryParameters["count"]?.toIntOrNull() ?: 20
                call.respondSuccess(
                    message = "Statistics fetched successfully",
                    data = dashboardUseCase.getTracesByPage(count, page)
                )
            }
            get("/top-consumers/"){
                val hours = call.queryParameters["hour"]?.toIntOrNull()
                val days = call.queryParameters["days"]?.toIntOrNull()
                val weeks = call.queryParameters["weeks"]?.toIntOrNull()
                val months = call.queryParameters["months"]?.toIntOrNull()
                val time = when {
                    hours != null -> hours
                    days != null -> 24 * days
                    weeks != null -> (24 * 7) * weeks
                    months != null -> (24 * 30) * months
                    else -> 1
                }
                call.respondSuccess(
                    message = "Statistics fetched successfully",
                    data = dashboardUseCase.getTopConsumersByHrs(hours = time)
                )

            }
            get("/service"){
                val services = currentServices()
                call.respondSuccess(message = "Messages fetched successfully", data = services)
            }
            get("/route"){
                val routes = routes.map {
                    KateRoutes(path = it.uri, method = it.method, route = it.route)
                }
                call.respondSuccess(message = "Routes fetched successfully", data = routes)
            }
            post("/route"){
                val service = call.receive<Service>()
                val response = findAndUpdateOrCreateService(listOf(service))
                startGateway(listOf(service), forwarder)
                call.respondSuccess(message = "", data = response)
            }
        }

        //get-routes
        // create routes
        //traces
        // analytics
    }
}

fun generateRandomToken(length: Int = 32): String {
    val allowedChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    return (1..length)
        .map { allowedChars.random() }
        .joinToString("")
}