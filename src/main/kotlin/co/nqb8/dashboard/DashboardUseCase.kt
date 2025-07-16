package co.nqb8.dashboard

import co.nqb8.dashboard.dto.*
import co.nqb8.data.RequestRepository
import co.nqb8.data.dto.RequestLogEntity
import co.nqb8.data.dto.RequestLogTable
import co.nqb8.gateway.routes
import io.ktor.http.*
import kotlinx.datetime.*
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
                    val time = LocalTime(it.key, 0)
                    FlowChart(title = time.toHourAmPm(), value = it.value.size.toLong())
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

    suspend fun getTracesByPage(page: Int, count: Int): List<Trace>{
        return newSuspendedTransaction {
            RequestLogEntity.all().offset(page.toLong()).limit(count).map {
                Trace(
                    id = it.uuid.toString(),
                    route = it.path,
                    status = HttpStatusCode.fromValue(it.responseStatusCode ?: 404).isSuccess(),
                    duration = it.latencyMs ?: 0,
                    timeStamp = it.createdAt,
                    method = it.httpMethod,
                    sourceIp = it.clientIp,
                    headers = it.requestHeaders,
                    upstreamDuration = it.responseTime ?: 0,
                    requestBody = it.requestBody.orEmpty(),
                    responseBody = it.responseBody.orEmpty(),
                    authType = it.authType,
                    authSuccess = it.authenticationSuccess
                )
            }
        }
    }

    suspend fun getTopConsumersByHrs(hours: Int = 24): List<TopConsumers>{
        val currentTime = Clock.System.now()
        val last24hours = (currentTime - hours.hours).toLocalDateTime(TimeZone.currentSystemDefault())
        return newSuspendedTransaction {
            requestRepository.find { RequestLogTable.createdAt greaterEq last24hours }
                .groupBy { it.path }
                .map { (key, entity) ->
                    val successes = entity.map { HttpStatusCode.fromValue(it.responseStatusCode ?: 404) }.filter { it.isSuccess() }
                    val errors = entity.map { HttpStatusCode.fromValue(it.responseStatusCode ?: 404) }.filter { !it.isSuccess() }
                    val errorPercentage = errors.count() / entity.size * 100
                    val latencies = entity.sumOf { it.latencyMs ?: 0 }
                    TopConsumers(
                        route = key,
                        requests = entity.size,
                        successRate = successes.count(),
                        averageLatency = latencies / entity.size,
                        errorRatePercent = errorPercentage
                    )
                }
        }
    }

    private fun LocalTime.toHourAmPm(): String {
        val hour = this.hour
        val amPm = if (hour < 12) "AM" else "PM"
        val hour12 = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        return "${hour12}${amPm}"
    }
}