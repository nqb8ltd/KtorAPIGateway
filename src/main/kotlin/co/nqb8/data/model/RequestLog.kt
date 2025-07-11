package co.nqb8.data.model

import co.nqb8.data.dto.AuthType
import kotlinx.serialization.Serializable

@Serializable
data class RequestLog(
    val timestamp: String,
    val clientIp: String,
    val httpMethod: String,
    val path: String,
    val requestHeaders: String,
    val responseHeaders: String,
    val upstreamUrl: String,
    val queryParams: String?,
    val uuid: String,
    val requestBody: String?,
    val responseBody: String?,
    val authType: AuthType,
    val authenticationSuccess: Boolean,
    val authorizationSuccess: Boolean,
    val responseStatusCode: Int?,
    val responseTime: Long?,
    val latencyMs: Long?,
    val createdAt: String,
    val updatedAt: String,
    val id: Int,
)
