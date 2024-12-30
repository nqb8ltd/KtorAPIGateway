package co.nqb8.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
data class Service(
    @SerialName("name")
    val name: String,
    @SerialName("baseUrl")
    val baseUrl: String,
    @SerialName("routes")
    val routes: List<Route> = listOf(),
    @SerialName("aggregates")
    val aggregates: List<Aggregate> = listOf(),
    @SerialName("message_queue")
    val messageQueue: MessageQueue? = null
)

@Serializable
data class Aggregate(
    @SerialName("uri")
    val uri: String,
    @SerialName("authentication_policy")
    val authenticationPolicy: AuthenticationPolicy? = null,
    @SerialName("routes")
    val routes: List<Route>,
    @SerialName("rate_limit_policy")
    val rateLimitPolicy: RateLimitPolicy? = null,
)

@Serializable
data class Route(
    @SerialName("uri")
    val uri: String,
    @SerialName("tag")
    val tag: String? = null,
    @SerialName("methods")
    val methods: List<String>? = null,
    @SerialName("baseUrl")
    val baseUrl: String? = null,
    @SerialName("authentication_policy")
    val authenticationPolicy: AuthenticationPolicy? = null,
    @SerialName("rate_limit_policy")
    val rateLimitPolicy: RateLimitPolicy? = null,
    @SerialName("queue")
    val queue: String? = null,
)

@Serializable
sealed class AuthenticationPolicy {
    abstract val policy: Policy
    abstract val check: String
    enum class Policy{
        VERIFY, PRESENT
    }
}

@Serializable
@SerialName("JwtPolicy")
data class JwtPolicy(
    override val policy: Policy,
    val jwtSecret: String,
    val data: String,
    val permissionsKey: String,
    val permissions: List<String> = listOf(),
    override val check: String = "",
    val checkPath: String = "",
): AuthenticationPolicy()

@Serializable
@SerialName("KeyPolicy")
data class KeyPolicy(
    override val policy: Policy,
    val verifyEndpoint: String,
    val keyHeader: String,
    override val check: String = "userId",
    val permissionsKey: String? = null,
    val permissionsKeys: List<String>? = null
): AuthenticationPolicy()



@Serializable
data class RateLimitPolicy(
    val limit: Int,
    val refreshTimeSeconds: Int,
    val requestKey: RequestKey = RequestKey.IP,
    val header: String = "",
){
    enum class RequestKey{
        IP, API_KEY, BEARER
    }
}

@Serializable
data class MessageQueue(
    val host: String,
    val port: Int,
    val username: String? = null,
    val password: String? = null,
    val transFormation: TransFormation? = null,
){
    @Serializable
    data class TransFormation(
        val fromKey: String,
        val toKey: String,
        val dataBody: String?, //meta=,event=,data=body //body
        val transformSource: TransformSource = TransformSource.KEY
    )
    enum class TransformSource{
        PATH, HEADER, KEY
    }
    fun getAddress() = "$host:$port"
}