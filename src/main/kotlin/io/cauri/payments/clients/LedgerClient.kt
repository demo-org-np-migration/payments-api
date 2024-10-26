package io.cauri.payments.clients

import com.fasterxml.jackson.databind.annotation.JsonSerialize
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.util.UUID

data class TransferRequest(
    @JvmField val from_account: UUID,
    @JvmField val to_account: UUID,
    @field:JsonSerialize(using = PlainDecimalSerializer::class)
    @JvmField val amount: BigDecimal,
    @JvmField val currency: String,
    @JvmField val reference: String,
)

data class LedgerEntry(
    val id: UUID? = null,
    val account_id: UUID? = null,
    val amount: BigDecimal? = null,
    val type: String? = null,
    val reference: String? = null,
    val created_at: String? = null,
)

data class TransferResponse(
    val debit_entry: LedgerEntry,
    val credit_entry: LedgerEntry,
)

class InsufficientFundsException(message: String) : RuntimeException(message)

/** Cliente a ledger-core (las convenciones internas de API). Autenticado con el token de servicio de payments-api. */
@Component
class LedgerClient(private val ledgerWebClient: WebClient) {

    fun transfer(request: TransferRequest): Mono<TransferResponse> =
        ledgerWebClient.post()
            .uri("/v1/ledger/transfers")
            .bodyValue(request)
            .retrieve()
            .onStatus({ it.value() == 422 }) { response ->
                response.bodyToMono(String::class.java)
                    .defaultIfEmpty("insufficient_funds")
                    .flatMap { Mono.error<Throwable>(InsufficientFundsException(it)) }
            }
            .bodyToMono(TransferResponse::class.java)
}
