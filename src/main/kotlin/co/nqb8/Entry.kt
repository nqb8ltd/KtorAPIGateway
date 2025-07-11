package co.nqb8

import co.nqb8.config.servicesApi
import co.nqb8.forwarder.Forwarder
import io.ktor.server.application.*
import io.ktor.util.*


fun Application.registerServices() {
    val forwarder = Forwarder()
    attributes.put(AttributeKey("forwarder"), forwarder)
    servicesApi(forwarder)
}








