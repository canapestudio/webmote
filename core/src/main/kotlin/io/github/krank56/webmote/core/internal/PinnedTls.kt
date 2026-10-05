package io.github.krank56.webmote.core.internal

import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.security.cert.Certificate
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

/**
 * TLS for TVs, whose certificates come from LG's private CA and can't be validated against public
 * roots. Webmote pins each TV's certificate at pairing (trust on first use) and from then on accepts
 * only that certificate. Nothing here ever accepts an arbitrary certificate for a connection that
 * carries data.
 */
internal object PinnedTls {

    /** SHA-256 of the certificate's DER encoding, as colon-separated upper-case hex. */
    fun fingerprint(certificate: Certificate): String =
        MessageDigest.getInstance("SHA-256").digest(certificate.encoded).joinToString(":") { "%02X".format(it) }

    /**
     * Reads the certificate the TV at [host]:[port] presents, without completing the handshake: the
     * trust manager records the certificate and then rejects it, so no data is ever exchanged.
     *
     * Throws [java.net.ConnectException] and friends when the TV doesn't accept the connection.
     */
    fun captureCertificate(host: String, port: Int, timeoutMillis: Int): X509Certificate {
        val trustManager = CapturingTrustManager()
        val context = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustManager), null) }
        (context.socketFactory.createSocket() as SSLSocket).use { socket ->
            socket.connect(InetSocketAddress(host, port), timeoutMillis)
            socket.soTimeout = timeoutMillis
            try {
                socket.startHandshake()
            } catch (_: SSLException) {
                // Expected: CapturingTrustManager always rejects.
            }
        }
        return trustManager.captured ?: throw SSLHandshakeException("The TV presented no certificate")
    }

    /** A client derived from [base] that only talks to a server presenting the certificate with [pin]. */
    fun pinnedClient(base: OkHttpClient, pin: String): OkHttpClient {
        val trustManager = PinnedTrustManager(pin)
        val context = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustManager), null) }
        return base.newBuilder()
            .sslSocketFactory(context.socketFactory, trustManager)
            // The TV's certificate doesn't name its IP address; the pin identifies it instead.
            .hostnameVerifier { _, session ->
                session.peerCertificates.firstOrNull()?.let(::fingerprint) == pin
            }
            .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS))
            .build()
    }
}

/** Thrown during a handshake when the TV presents a certificate other than the pinned one. */
internal class CertificateMismatchException(message: String) : CertificateException(message)

private class PinnedTrustManager(private val pin: String) : X509TrustManager {
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        val leaf = chain?.firstOrNull() ?: throw CertificateException("The TV presented no certificate")
        val presented = PinnedTls.fingerprint(leaf)
        if (presented != pin) throw CertificateMismatchException("Expected certificate $pin, got $presented")
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        throw CertificateException("Client certificates are not accepted")
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

private class CapturingTrustManager : X509TrustManager {
    @Volatile var captured: X509Certificate? = null

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        captured = chain?.firstOrNull()
        throw CertificateException("Certificate recorded for pinning; this connection is not used")
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        throw CertificateException("Client certificates are not accepted")
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

/** Whether [error] (or one of its causes) is a pinned-certificate mismatch. */
internal fun isCertificateMismatch(error: Throwable): Boolean =
    generateSequence(error) { it.cause }.any { it is CertificateMismatchException } ||
        (error is javax.net.ssl.SSLPeerUnverifiedException)
