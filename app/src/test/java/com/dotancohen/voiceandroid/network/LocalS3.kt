package com.dotancohen.voiceandroid.network

import java.io.File
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.net.URLEncoder
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * moto's S3 server on this computer, with authentication on: the key is made
 * in its own IAM, every request of the core must be signed with it, and a
 * wrong secret is refused. The same server as the desktop's tests/local_s3.py.
 *
 * moto checks signatures only after its first INITIAL_NO_AUTH_ACTION_COUNT
 * requests. Those requests are the ones that make the user, its policy and
 * its key, and one bucket per test (the phone's bindings make no buckets);
 * they carry an authorization header that names the service, which moto
 * routes by and does not check in that window.
 */
class LocalS3 private constructor(
    private val process: Process,
    val port: Int,
    val accessKeyId: String,
    val secretAccessKey: String,
    private val buckets: ArrayDeque<String>,
) : AutoCloseable {

    val endpoint: String get() = "http://127.0.0.1:$port"

    /** A bucket no other test uses. */
    fun takeBucket(): String = buckets.removeFirst()

    /** The storage configuration a device saves, reaching the bucket at [endpoint]. */
    fun configJson(bucket: String, endpoint: String = this.endpoint, secret: String = secretAccessKey): String =
        """{"bucket": "$bucket", "region": "$REGION", "access_key_id": "$accessKeyId", "secret_access_key": "$secret", "endpoint": "$endpoint"}"""

    override fun close() {
        process.destroy()
        process.waitFor()
    }

    companion object {
        const val REGION = "eu-central-1"
        private const val BUCKETS = 12

        fun start(): LocalS3 {
            val server = System.getProperty("voice.test.motoServer")
                ?: error("voice.test.motoServer is not set; run the tests through Gradle")
            check(File(server).canExecute()) { "moto's server is not at $server; the s3ServerForTests task installs it" }
            val port = ServerSocket(0).use { it.localPort }
            val builder = ProcessBuilder(server, "-H", "127.0.0.1", "-p", port.toString())
                .redirectErrorStream(true)
                // Android's API has no Redirect.DISCARD
                .redirectOutput(ProcessBuilder.Redirect.to(File("/dev/null")))
            builder.environment()["INITIAL_NO_AUTH_ACTION_COUNT"] = (3 + BUCKETS).toString()
            val process = builder.start()
            val deadline = System.currentTimeMillis() + 30_000
            while (true) {
                try {
                    Socket("127.0.0.1", port).close()
                    break
                } catch (e: Exception) {
                    check(System.currentTimeMillis() < deadline) { "moto did not start on port $port" }
                    Thread.sleep(200)
                }
            }
            val endpoint = "http://127.0.0.1:$port"
            iam(endpoint, "CreateUser", "UserName" to "voice")
            iam(
                endpoint, "PutUserPolicy", "UserName" to "voice", "PolicyName" to "voice-buckets",
                "PolicyDocument" to """{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Action":"s3:*","Resource":"*"}]}"""
            )
            val key = iam(endpoint, "CreateAccessKey", "UserName" to "voice")
            val accessKeyId = key.substringAfter("<AccessKeyId>").substringBefore("</AccessKeyId>")
            val secret = key.substringAfter("<SecretAccessKey>").substringBefore("</SecretAccessKey>")
            val buckets = ArrayDeque<String>()
            repeat(BUCKETS) { n ->
                val name = "voice-a%05d".format(n + 1)
                val body = "<CreateBucketConfiguration><LocationConstraint>$REGION</LocationConstraint></CreateBucketConfiguration>"
                val answer = request("PUT", "$endpoint/$name", "s3", body, "application/xml")
                check(answer.first in 200..299) { "bucket $name was not made: ${answer.second}" }
                buckets += name
            }
            return LocalS3(process, port, accessKeyId, secret, buckets)
        }

        private fun iam(endpoint: String, action: String, vararg fields: Pair<String, String>): String {
            val form = (listOf("Action" to action, "Version" to "2010-05-08") + fields)
                .joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }
            val answer = request("POST", "$endpoint/", "iam", form, "application/x-www-form-urlencoded")
            check(answer.first in 200..299) { "$action failed: ${answer.second}" }
            return answer.second
        }

        /** A request in moto's unauthenticated window: the header names the service and is not checked. */
        private fun request(method: String, url: String, service: String, body: String, contentType: String): Pair<Int, String> {
            val date = ZonedDateTime.now(ZoneOffset.UTC)
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = method
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", contentType)
            connection.setRequestProperty("X-Amz-Date", date.format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")))
            connection.setRequestProperty(
                "Authorization",
                "AWS4-HMAC-SHA256 Credential=unsigned/${date.format(DateTimeFormatter.BASIC_ISO_DATE).take(8)}/us-east-1/$service/aws4_request, SignedHeaders=host, Signature=0"
            )
            connection.outputStream.use { it.write(body.toByteArray()) }
            val status = connection.responseCode
            val text = (if (status < 400) connection.inputStream else connection.errorStream)?.bufferedReader()?.readText().orEmpty()
            return status to text
        }
    }
}
