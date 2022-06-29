package io.cauri.payments.clients

import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.util.UUID

data class ScoreRequest(
    @JvmField val payment_id: UUID,
    @JvmField val from_account: UUID,
    @JvmField val to_account: UUID,
    @JvmField val amount: BigDecimal,
    @JvmField val currency: String,
)

enum class FraudDecision {
    approve, review, reject
}

data class ScoreResponse(
    val score: BigDecimal,
    val decision: FraudDecision,
    val signals: List<String> = emptyList(),
)

/** Cliente a fraud-scoring (las convenciones internas de API). */
@Component
class FraudClient(private val fraudWebClient: WebClient) {

    fun score(request: ScoreRequest): Mono<ScoreResponse> =
        fraudWebClient.post()
            .uri("/v1/score")
            .bodyValue(request)
            .retrieve()
            .bodyToMono(ScoreResponse::class.java)
}
