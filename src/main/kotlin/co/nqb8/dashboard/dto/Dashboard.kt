package co.nqb8.dashboard.dto

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class Login(
    val email: String,
    val password: String
)

@Serializable
data class DashboardHome(
    val totalApiCount: Int,
    val requestVolume: Long,
    val averageLatency: Long,
    val errorRatePercent: Double,
    val last24HourIncreasePercent: Double,
    val last7HoursFlow: List<FlowChart>,
    val recentRequestsWithIssue: List<LastRequestIssue>
)

@Serializable
data class LastRequestIssue(
    val uuid: String,
    val time: LocalDateTime,
    val path: String,
    val upstream: String?,
    val responseStatus: Int
)
@Serializable
data class FlowChart(
    val title: String,
    val value: Long,
)