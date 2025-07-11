package co.nqb8.config

import co.nqb8.data.dto.RequestLogTable
import io.ktor.server.application.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.exists
import org.jetbrains.exposed.sql.transactions.transaction

fun Application.configureDatabases() {
    val url = environment.config.property("postgres.url").getString().ifEmpty { "jdbc:postgresql://localhost:5432/kate" }
    val user = environment.config.property("postgres.user").getString().ifEmpty { "postgres" }
    val password = environment.config.property("postgres.password").getString().ifEmpty { "password" }
    Database.connect(
        url = url,
        user = user,
        driver = "org.postgresql.Driver",
        password = password
    )
}

fun runMigrations() {
    transaction {
        // 1. Create tables that don't exist
        val tables = listOf(
            RequestLogTable
        )
        val tablesToCreate = tables.filterNot { it.exists() }
        if (tablesToCreate.isNotEmpty()) {
            val createTableStatements = SchemaUtils.createStatements(*tablesToCreate.toTypedArray())
            println("Creating tables: ${tablesToCreate.joinToString { it.tableName }}")
            execInBatch(createTableStatements)
        }

        // 2. Add missing columns to existing tables
        // This is generally safe and won't drop sequences.
        val missingColumnStatements = SchemaUtils.addMissingColumnsStatements(*tables.toTypedArray())
        if (missingColumnStatements.isNotEmpty()) {
            println("Adding missing columns statements: ${missingColumnStatements.joinToString(";\n")}")
            execInBatch(missingColumnStatements)
        }
    }


}