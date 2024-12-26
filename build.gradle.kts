
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
        val API_KEY: String? = project.properties.get("API_KEY") as String?
        println("Key: $API_KEY")
        environment = mapOf("API_KEY" to API_KEY)
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

    implementation(libs.rabbitmq)


    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.client.json)
    implementation(libs.ktor.client.neg)


    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test.junit)
}
