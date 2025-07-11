package co.nqb8.dashboard

import co.nqb8.dashboard.dto.DashboardHome
import co.nqb8.dashboard.dto.FlowChart
import co.nqb8.dashboard.dto.LastRequestIssue
import co.nqb8.data.RequestRepository
import co.nqb8.data.dto.RequestLogTable
import co.nqb8.gateway.routes
import io.ktor.http.*
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import kotlin.time.Duration.Companion.hours

class DashboardUseCase(
    private val requestRepository: RequestRepository
) {
    suspend fun getHomeStatistics(): DashboardHome {
        return newSuspendedTransaction {
            val totalApiCount = routes.size
            val currentTime = Clock.System.now()
            val last7hrs = (currentTime - 7.hours).toLocalDateTime(TimeZone.currentSystemDefault())
            val last24hours = (currentTime - 24.hours).toLocalDateTime(TimeZone.currentSystemDefault())
            val last48hours = (last24hours.toInstant(TimeZone.currentSystemDefault()) - 24.hours).toLocalDateTime(TimeZone.currentSystemDefault())
            val requestVolume = requestRepository.runQuery { RequestLogTable.createdAt greaterEq last24hours }.count()
            val requestBy24hrs = requestRepository.find { RequestLogTable.createdAt greaterEq last24hours }
            val averageLatency = requestBy24hrs.mapNotNull { it.latencyMs  }.sum() / requestBy24hrs.count()
            val errorRate = requestBy24hrs
                .asSequence()
                .mapNotNull { it.responseStatusCode }
                .map { HttpStatusCode.fromValue(it) }
                .filter { !it.isSuccess() }
            val errorRatePercent = (errorRate.count().toDouble() / requestBy24hrs.count()) * 100
            val requestBy48hrs = requestRepository.find {
                (RequestLogTable.createdAt greaterEq last48hours) and (RequestLogTable.createdAt less last24hours)
            }.count()
            val last24HourIncreasePercent = (requestBy48hrs.toDouble() / requestBy24hrs.count()) * 100
            val requestBy7hrs = requestRepository.find { RequestLogTable.createdAt greaterEq last7hrs }
                .groupBy { it.updatedAt.hour }
                .map {
                    FlowChart(title = "${it.key}AM", value = it.value.size.toLong())
                }
            val recentRequestsWithIssue = requestRepository.find {
                RequestLogTable.responseStatusCode notInList  (200 until 300)
            }.sortedByDescending { it.createdAt }
                .take(5).map {
                    LastRequestIssue(
                        uuid = it.uuid.toString(),
                        time = it.updatedAt,
                        path = it.path,
                        upstream = it.upstreamUrl,
                        responseStatus = it.responseStatusCode ?: 0
                    )
                }
            DashboardHome(
                totalApiCount = totalApiCount,
                requestVolume = requestVolume,
                averageLatency = averageLatency,
                errorRatePercent = errorRatePercent,
                last24HourIncreasePercent = last24HourIncreasePercent,
                last7HoursFlow = requestBy7hrs,
                recentRequestsWithIssue = recentRequestsWithIssue
            )
        }
    }
}