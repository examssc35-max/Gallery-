package com.example.data.cloudflare

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object AwsSigV4Signer {

    private const val HMAC_SHA256 = "HmacSHA256"
    private const val ALGORITHM = "AWS4-HMAC-SHA256"

    data class SignResult(
        val authorization: String,
        val amzDate: String,
        val payloadHash: String
    )

    fun sign(
        method: String,
        host: String,
        path: String,
        queryParams: Map<String, String>,
        headers: Map<String, String>,
        payloadHash: String,
        accessKeyId: String,
        secretAccessKey: String,
        region: String = "auto",
        service: String = "s3",
        date: Date = Date()
    ): SignResult {
        val dateFormat = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val dateStampFormat = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        val amzDate = dateFormat.format(date)
        val dateStamp = dateStampFormat.format(date)

        val allHeaders = headers.toMutableMap()
        allHeaders["host"] = host
        allHeaders["x-amz-date"] = amzDate
        allHeaders["x-amz-content-sha256"] = payloadHash

        // Canonical URI
        val canonicalUri = if (path.isEmpty()) "/" else {
            path.split("/").joinToString("/") { segment ->
                urlEncode(segment)
            }
        }

        // Canonical Query String
        val canonicalQuery = queryParams.entries
            .sortedBy { it.key }
            .joinToString("&") { (k, v) ->
                "${urlEncode(k)}=${urlEncode(v)}"
            }

        // Canonical Headers
        val sortedHeaders = allHeaders.mapKeys { it.key.lowercase(Locale.US).trim() }
            .toSortedMap()

        val canonicalHeaders = StringBuilder()
        for ((k, v) in sortedHeaders) {
            canonicalHeaders.append(k).append(":").append(v.trim()).append("\n")
        }

        val signedHeaders = sortedHeaders.keys.joinToString(";")

        // Canonical Request
        val canonicalRequest = "$method\n$canonicalUri\n$canonicalQuery\n$canonicalHeaders\n$signedHeaders\n$payloadHash"

        // String to Sign
        val credentialScope = "$dateStamp/$region/$service/aws4_request"
        val hashedCanonicalRequest = sha256Hex(canonicalRequest.toByteArray(StandardCharsets.UTF_8))
        val stringToSign = "$ALGORITHM\n$amzDate\n$credentialScope\n$hashedCanonicalRequest"

        // Signing Key
        val kSigning = getSignatureKey(secretAccessKey, dateStamp, region, service)
        val signature = bytesToHex(hmacSha256(kSigning, stringToSign))

        val authHeader = "$ALGORITHM Credential=$accessKeyId/$credentialScope, SignedHeaders=$signedHeaders, Signature=$signature"

        return SignResult(
            authorization = authHeader,
            amzDate = amzDate,
            payloadHash = payloadHash
        )
    }

    private fun getSignatureKey(
        key: String,
        dateStamp: String,
        regionName: String,
        serviceName: String
    ): ByteArray {
        val kSecret = ("AWS4$key").toByteArray(StandardCharsets.UTF_8)
        val kDate = hmacSha256(kSecret, dateStamp)
        val kRegion = hmacSha256(kDate, regionName)
        val kService = hmacSha256(kRegion, serviceName)
        return hmacSha256(kService, "aws4_request")
    }

    fun sha256Hex(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return bytesToHex(digest.digest(data))
    }

    fun emptyPayloadHash(): String =
        "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

    private fun hmacSha256(key: ByteArray, data: String): ByteArray {
        val mac = Mac.getInstance(HMAC_SHA256)
        mac.init(SecretKeySpec(key, HMAC_SHA256))
        return mac.doFinal(data.toByteArray(StandardCharsets.UTF_8))
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append(String.format("%02x", b.toInt() and 0xFF))
        }
        return sb.toString()
    }

    private fun urlEncode(value: String): String {
        return URLEncoder.encode(value, "UTF-8")
            .replace("+", "%20")
            .replace("*", "%2A")
            .replace("%7E", "~")
    }
}
