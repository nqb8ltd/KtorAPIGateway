package co.nqb8.dashboard

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

@Serializable
data class PagedResult<T>(
    val page: Int,
    val count: Int,
    val total: Long,
    val hasNext: Boolean,
    val hasPrevious: Boolean,
    val items: List<T>
)

suspend fun <T, R> pagedQuery(
    page: Int,
    count: Int,
    queryOp: suspend () -> SizedIterable<T>,
    transform: (T) -> R
): PagedResult<R> {
    return newSuspendedTransaction {
        val query = queryOp()
        val total = query.count()

        val offset = ((page - 1) * count).toLong()

        val items = query
            .offset(offset)
            .limit(count)
            .map(transform)

        PagedResult(
            page = page,
            count = count,
            total = total,
            hasNext = (offset + count) < total,
            hasPrevious = page > 1,
            items = items
        )
    }
}
