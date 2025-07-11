package co.nqb8.data.dto

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.kotlin.datetime.CurrentDateTime
import org.jetbrains.exposed.sql.kotlin.datetime.datetime

object RequestLogTable : IntIdTable("request_log") {
    val uuid = uuid("uuid").uniqueIndex()
    val clientIp        = varchar("client_ip", length = 12)
    val httpMethod      = text("http_method")
    val path            = text("path")
    val requestHeaders  = text("request_headers")

    val startTimestamp       = datetime("start_timestamp").defaultExpression(CurrentDateTime) //done
    val endTimestamp       = datetime("end_timestamp").nullable() //done

    val upstreamUrl = varchar("upstream_url", length = 100).nullable() // done
    val responseHeaders         = text("response_headers").nullable() // done
    val responseBody            = text("response_body").nullable() // done
    val responseStatusCode      = integer("response_status_code").nullable() //done
    val responseTime = long("response_time").nullable() //done

    val queryParams     = text("query_params").nullable() //Done
    val requestBody            = text("request_body").nullable() // done

    val authType = enumerationByName("auth_type", 20, AuthType::class).default(AuthType.NONE) //done
    val authenticationSuccess = bool("authentication_success").default(false)  //done

    val latencyMs       = long("latency_ms").nullable()
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val updatedAt = datetime("updated_at").defaultExpression(CurrentDateTime)
}

class RequestLogEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<RequestLogEntity>(RequestLogTable)

    var uuid by RequestLogTable.uuid
    var startTimestamp by RequestLogTable.startTimestamp
    var endTimestamp by RequestLogTable.endTimestamp
    var clientIp by RequestLogTable.clientIp
    var httpMethod by RequestLogTable.httpMethod
    var path by RequestLogTable.path
    var requestHeaders by RequestLogTable.requestHeaders
    var responseHeaders by RequestLogTable.responseHeaders
    var upstreamUrl by RequestLogTable.upstreamUrl
    var queryParams by RequestLogTable.queryParams
    var requestBody by RequestLogTable.requestBody
    var responseBody by RequestLogTable.responseBody
    var authType by RequestLogTable.authType
    var authenticationSuccess by RequestLogTable.authenticationSuccess
    var responseStatusCode by RequestLogTable.responseStatusCode
    var responseTime by RequestLogTable.responseTime
    var latencyMs by RequestLogTable.latencyMs
    val createdAt by RequestLogTable.createdAt
    var updatedAt by RequestLogTable.updatedAt

}