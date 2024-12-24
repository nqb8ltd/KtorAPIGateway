package co.nqb8.auth

import co.nqb8.config.AuthenticationPolicy
import com.auth0.jwt.interfaces.DecodedJWT
import io.ktor.util.*
import io.ktor.util.collections.*
import kotlinx.serialization.json.JsonObject

internal val GatewayJWTAuthForCallKey = AttributeKey<DecodedJWT>("GatewayJWTAuthForCallKey")
internal val GatewayKeyAuthForCallKey = AttributeKey<JsonObject>("GatewayKeyAuthForCallKey")
val AuthProviders = ConcurrentMap<String, AuthenticationPolicy>()
