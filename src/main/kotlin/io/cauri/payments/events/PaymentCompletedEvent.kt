package io.cauri.payments.events

import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** Payload de `<KAFKA_TOPIC_PREFIX>payments.completed` (las convenciones internas de API). */
data class PaymentCompletedEvent(
    val event: String = "payments.completed",
    val version: Int = 1,
    @JsonProperty("payment_id") val paymentId: UUID,
    @JsonProperty("from_account") val fromAccount: UUID,
    @JsonProperty("to_account") val toAccount: UUID,
    @JsonProperty("merchant_id") val merchantId: UUID?,
    val amount: String,
    val currency: String,
    @JsonProperty("occurred_at") val occurredAt: Instant,
) {
    companion object {
        fun of(paymentId: UUID, fromAccount: UUID, toAccount: UUID, merchantId: UUID?, amount: BigDecimal, currency: String): PaymentCompletedEvent =
            PaymentCompletedEvent(
                paymentId = paymentId,
                fromAccount = fromAccount,
                toAccount = toAccount,
                merchantId = merchantId,
                amount = amount.toPlainString(),
                currency = currency,
                occurredAt = Instant.now(),
            )
    }
}
