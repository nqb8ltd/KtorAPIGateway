package co.nqb8

import co.nqb8.config.Service
import co.nqb8.config.configureGateway
import co.nqb8.pipeline.Forwarder
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.*
import io.ktor.server.application.*
import io.ktor.util.*
import kotlinx.serialization.json.Json
import kotlin.io.path.Path
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid


fun Application.registerServices(client: HttpClient, json: Json) {
    val forwarder = Forwarder(client)
    attributes.put(AttributeKey("forwarder"), forwarder)
   val services = runCatching {
        val config = Path("services.json").toFile().readText()
        json.decodeFromString<List<Service>>(config)
    }.getOrNull()


    configureGateway(services, forwarder)

}




@OptIn(ExperimentalUuidApi::class)
fun generate(){
//    val secret = environment.config.property("jwt.secret").getString()
//    val issuer = environment.config.property("jwt.issuer").getString()
//    val audience = environment.config.property("jwt.audience").getString()
//    val myRealm = environment.config.property("jwt.realm").getString()

    val token = JWT.create()
//        .withAudience(audience)
        .withIssuer("issuer")
        .withClaim("userId", Uuid.random().toString())
        .withPayload(mapOf("/users/{userId}" to listOf("GET")))
        .sign(Algorithm.HMAC256("secret"))
    println(token)
}







