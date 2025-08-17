
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.serialization)
}

group = "co.nqb8"
version = "0.0.1"

application {
    mainClass.set("io.ktor.server.netty.EngineMain")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
    mavenCentral()
    maven {
        url = uri("https://packages.confluent.io/maven")
        name = "confluence"
    }
}
jib{
    from {
        platforms{
            platform {
                architecture = "arm64"
                os = "linux"
            }
        }
    }
    container{
        ports = listOf("8080")
        val API_KEY: String? = project.properties["API_KEY"] as String?
        println("Key: $API_KEY")
        val DB_URL = project.properties["DB_URL"] as String?
        val DB_USER = project.properties["DB_USER"] as String?
        val DB_PASSWORD = project.properties["DB_PASSWORD"] as String?
        val ADMIN_EMAIL = project.properties["ADMIN_EMAIL"] as String?
        val ADMIN_PASSWORD = project.properties["ADMIN_PASSWORD"] as String?
        environment = mapOf(
            "API_KEY" to API_KEY,
            "DB_URL" to DB_URL,
            "DB_USER" to DB_USER,
            "DB_PASSWORD" to DB_PASSWORD,
            "ADMIN_EMAIL" to ADMIN_EMAIL,
            "ADMIN_PASSWORD" to ADMIN_PASSWORD
        )
    }
}
ktor{
    docker{
        this.
        jreVersion.set(JavaVersion.VERSION_21)
        localImageName.set("api/gateway")
        portMappings.set(listOf(
            io.ktor.plugin.features.DockerPortMapping(
                6060,
                8080,
                io.ktor.plugin.features.DockerPortMappingProtocol.TCP
            )
        ))
    }
}

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.host.common)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.csrf)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.ktor.server.netty)
    implementation(libs.logback.classic)
    implementation(libs.ktor.server.neg)
    implementation(libs.ktor.server.json)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.rate.limit)
    implementation(libs.ktor.server.call.id)
    implementation(libs.ktor.server.double.receive)

    implementation(libs.rabbitmq)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.apache)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.client.json)
    implementation(libs.ktor.client.neg)

    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.migration)
    implementation(libs.exposed.kotlin.datetime)
    implementation(libs.postgres)

    implementation(libs.date)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test.junit)
}
