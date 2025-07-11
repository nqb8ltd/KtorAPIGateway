package co.nqb8

import co.nqb8.config.configureDatabases
import co.nqb8.config.runMigrations
import co.nqb8.plugins.configurePlugins
import io.ktor.server.application.*
import io.ktor.server.netty.*

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    configureDatabases()
    runMigrations()
    configurePlugins()
    registerServices()
}