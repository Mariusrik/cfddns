import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Records(val result: List<Record>)

@Serializable
data class Record(
    val id: String,
    val zone_id: String,
    val zone_name: String,
    val name: String,
    val type: String,
    val content: String
)

@Serializable
data class RecordContent(val content: String)

const val BASE_URL = "https://api.cloudflare.com/client/v4/zones"

fun getClient(): HttpClient {
    return HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
    }
}

suspend fun httpGet(client: HttpClient, location: String, token: String): HttpResponse {
    return client.get(BASE_URL) {
        configureRequest(location, token)
    }
}

fun HttpRequestBuilder.configureRequest(location: String, token: String) {
    url {
        appendPathSegments(location, "dns_records")
    }
    headers {
        append(HttpHeaders.Authorization, "Bearer $token")
        append(HttpHeaders.ContentType, "application/json")
    }
}

suspend fun main(args: Array<String>) {
    val argsMap = args.asSequence()
        .chunked(2) { it.first() to it.getOrNull(1) }
        .filter { it.first.startsWith("-") }
        .toMap()

    val location = argsMap["-location"]
    val token = argsMap["-token"]

    if (location == null) {
        println("need to pass -location x")
        return
    }

    if (token == null) {
        println("need to pass -token x")
        return
    }

    val client = getClient()
    val myIp = fetchMyIp(client)
    if (myIp == "Unknown IP"){
        println("ip unknown")
        return
    }

    updateRecords(client, location, token, myIp)
    client.close()
}

suspend fun updateRecords(client: HttpClient, location: String, token: String, myIp: String) {
    val response: HttpResponse = httpGet(client, location, token)
    if (response.status.value in 200..299) {
        println(response.status)
        println("Got DNS for records, jefe")
    } else {
        println(response.status)
        println("Error getting DNS record exiting.")
        return
    }

    val records: Records = response.body()
    val recordContent = RecordContent(myIp)

    for (record in records.result) {
        val updateRes = updateRecord(client, location, token, record.id, recordContent)
        if (updateRes.status.value in 200..299) {
            println(updateRes.status)
            println("ip address for ${record.name} updated jefe")
        } else {
            println(updateRes.status)
        }
    }
}

suspend fun fetchMyIp(client: HttpClient): String {
    val txt = client.get("https://cloudflare.com/cdn-cgi/trace").body<String>()
    val ip = txt.lines().firstOrNull { it.startsWith("ip") }?.substringAfter("ip=") ?: "Unknown IP"

    println("my ip is $ip")
    return ip
}

suspend fun updateRecord(client: HttpClient, location: String, token: String, id: String, recordContent: RecordContent): HttpResponse {
    return client.patch("$BASE_URL/$location/dns_records/$id") {
        headers {
            append(HttpHeaders.Authorization, "Bearer $token")
            append(HttpHeaders.ContentType, "application/json")
        }
        setBody(recordContent)
    }
}


