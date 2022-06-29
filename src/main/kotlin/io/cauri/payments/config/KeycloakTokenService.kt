package io.cauri.payments.config

import com.fasterxml.jackson.annotation.JsonProperty
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Client-credentials contra Keycloak (las convenciones internas de API): payments-api pide su propio token de
 * servicio para llamar a ledger-core y fraud-scoring. Cachea el access_token hasta un poco antes
 * de que expire para no pedir uno por request.
 */
@Component
class KeycloakTokenService(
    @Value("\${keycloak.issuer}") private val issuer: String,
    @Value("\${keycloak.client-id}") private val clientId: String,
    @Value("\${keycloak.client-secret}") private val clientSecret: String,
) {
    private val log = LoggerFactory.getLogger(KeycloakTokenService::class.java)
    private val tokenClient = WebClient.builder().build()
    private val lock = ReentrantLock()

    @Volatile
    private var cachedToken: CachedToken? = null

    /** Margen para no usar un token que expira mientras viaja el request. */
    private val expiryLeeway = java.time.Duration.ofSeconds(10)

    fun getAccessToken(): Mono<String> {
        cachedToken?.let { cached ->
            if (Instant.now().isBefore(cached.expiresAt.minus(expiryLeeway))) {
                return Mono.just(cached.accessToken)
            }
        }
        return fetchToken()
    }

    private fun fetchToken(): Mono<String> {
        lock.withLock {
            cachedToken?.let { cached ->
                if (Instant.now().isBefore(cached.expiresAt.minus(expiryLeeway))) {
                    return Mono.just(cached.accessToken)
                }
            }
        }
        val body = LinkedMultiValueMap<String, String>().apply {
            add("grant_type", "client_credentials")
            add("client_id", clientId)
            add("client_secret", clientSecret)
        }
        return tokenClient.post()
            .uri("$issuer/protocol/openid-connect/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .bodyValue(body)
            .retrieve()
            .bodyToMono(TokenResponse::class.java)
            .map { response ->
                val requestedAt = Instant.now()
                lock.withLock {
                    cachedToken = CachedToken(
                        accessToken = response.accessToken,
                        expiresAt = requestedAt.plusSeconds(response.expiresIn),
                    )
                }
                response.accessToken
            }
            .doOnError { err -> log.error("failed to obtain client-credentials token from {}: {}", issuer, err.message) }
    }

    private data class CachedToken(val accessToken: String, val expiresAt: Instant)

    data class TokenResponse(
        @JsonProperty("access_token") val accessToken: String,
        @JsonProperty("expires_in") val expiresIn: Long,
        @JsonProperty("token_type") val tokenType: String? = null,
    )
}
