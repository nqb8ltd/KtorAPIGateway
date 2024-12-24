package co.nqb8.utils

import io.ktor.http.*
import io.ktor.server.routing.*


/**
 * Build child route.
 *
 * @param parentUrl
 * @param childUrl
 * @param callUrl
 * @return full path for the child route
 */
internal fun buildChildRoute(parentUrl: Url, childUrl: Url, callUrl: Url): String{
    val callPaths = callUrl.segments
    val callQueries = callUrl.queryToMap()
    val path = mapPath(childUrl, callPaths, parentUrl.segments)
    return mapQueryParameters(path, childUrl, callQueries)
}


/**
 * Parent path variables are mapped to individual child paths
 *
 * @param route [Url] child aggregate route
 * @param callPaths [RoutingRequest] from the call (call.request.uri.segments)
 * @param definedUri Defined parent aggregate route.
 */
internal fun mapPath(route: Url, callPaths: List<String>, definedUri: List<String>): List<String> {
    return route.segments.map { segment ->
        if (segment.contains(Regex("\\{([a-zA-Z0-9_]+)}"))) callPaths[definedUri.indexOf(segment)]
        else segment
    }
}

/**
 * Map request url queries to map
 * @return [Map]
 */
internal fun Url.queryToMap(): Map<String, String> {
    return buildMap{
        val queries = encodedQuery
        if (queries.isNotEmpty()){
            queries.split("&").forEach {
                val (key, value) = it.split("=")
                put(key, value)
            }
        }
    }
}

/**
 * Build the final uri for child routes.
 * Paths and variables are joined, also queries are set if defined
 * @param path child paths and variables to join
 * @param route child route definition
 * @param callQueries [RoutingRequest] from the call (call.request.uri.segments)
 * @return the full url for the child path
 */
internal fun mapQueryParameters(path: List<String>, route: Url, callQueries: Map<String, String>): String {
    return buildString {
        append(path.joinToString("/"))
        if (route.encodedQuery.isNotEmpty()) {
            val routeQueries = route.queryToMap()
            var queryCount = 0
            routeQueries.forEach { (key, _) ->
                if (callQueries.containsKey(key)) {
                    if (queryCount > 0) append("&") else append("?")
                    append("$key=${callQueries[key]}")
                    queryCount++
                }
            }
        }
    }
}

fun uriMatches(registeredUri: String, callUri: String): Boolean{
    val registeredUrl = Url(registeredUri).segments
    val callUrl = Url(callUri).segments
    val registered = registeredUrl.toTypedArray()
    if (registered.size != callUrl.size) return false
    registered.forEachIndexed { index, segment ->
        val callSegment = callUrl[index]
        if (!segment.startsWith('{') && callSegment == segment){
            registered[index] = callSegment
        }
        if (segment.startsWith('{')){
            registered[index] = callSegment
        }
    }
    return callUrl == registered.toList()
}