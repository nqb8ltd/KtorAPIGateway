package co.nqb8.data

import co.nqb8.data.dto.RequestLogEntity
import co.nqb8.data.dto.RequestLogTable
import co.nqb8.plugins.json
import io.ktor.server.application.*
import io.ktor.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonElement
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.SizedIterable
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.*

class RequestRepository(
    private val scope: CoroutineScope
) {

    fun createRequestLog(
        requestId: String,
        clientIp: String,
        httpMethod: String,
        path: String,
        requestHeaders: String,
        query: String,
        body: String
    ) {
        scope.launch(Dispatchers.IO) {
            newSuspendedTransaction(Dispatchers.IO) {
                RequestLogEntity.new {
                    this.uuid = UUID.fromString(requestId)
                    this.clientIp = clientIp
                    this.httpMethod = httpMethod
                    this.path = path
                    this.requestHeaders = requestHeaders
                    this.queryParams = query
                    this.requestBody = body
                }
            }
        }
    }

    fun update(id: String?, requestLogEntity: (RequestLogEntity) -> Unit){
        scope.launch {
            newSuspendedTransaction(Dispatchers.IO) {
                RequestLogEntity.findSingleByAndUpdate(RequestLogTable.uuid eq UUID.fromString(id)){
                    it.updatedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                    requestLogEntity(it)
                }
            }
        }
    }

    suspend fun find(query: SqlExpressionBuilder.() -> Op<Boolean>): SizedIterable<RequestLogEntity> {
        return newSuspendedTransaction { RequestLogEntity.find { query() } }
    }
    suspend fun runQuery(query: () -> Op<Boolean>): Query {
        return newSuspendedTransaction {
            RequestLogEntity.searchQuery(query())
        }
    }



    companion object {
        const val KEY = "RequestRepository"
    }

}

val Application.requestRepository get() = attributes[AttributeKey<RequestRepository>(RequestRepository.KEY)]

fun String.isUtf8Compatible(): Boolean {
    return try {
        json.decodeFromString<JsonElement>(this)
        true
    } catch (e: Exception) {
        false
    }
}