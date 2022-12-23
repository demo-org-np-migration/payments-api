package io.cauri.payments.api

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import io.cauri.payments.domain.Merchant
import io.cauri.payments.domain.Payment
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class CreatePaymentRequest(
    @field:NotNull val from_account: UUID,
    @field:NotNull val to_account: UUID,
    @field:NotNull @field:Positive val amount: BigDecimal,
    @field:NotBlank val currency: String,
    @field:NotBlank val reference: String,
)

data class ChargeRequest(
    @field:NotNull val from_account: UUID,
    @field:NotNull @field:Positive val amount: BigDecimal,
    @field:NotBlank val currency: String,
    @field:NotBlank val reference: String,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class PaymentResponse(
    val id: UUID,
    @JsonProperty("from_account") val fromAccount: UUID,
    @JsonProperty("to_account") val toAccount: UUID,
    @JsonProperty("merchant_id") val merchantId: UUID?,
    val amount: String,
    val currency: String,
    val status: String,
    @JsonProperty("fraud_score") val fraudScore: BigDecimal?,
    @JsonProperty("created_at") val createdAt: Instant,
    @JsonProperty("updated_at") val updatedAt: Instant,
) {
    companion object {
        fun from(payment: Payment): PaymentResponse = PaymentResponse(
            id = payment.id,
            fromAccount = payment.fromAccount,
            toAccount = payment.toAccount,
            merchantId = payment.merchantId,
            amount = payment.amount.toPlainString(),
            currency = payment.currency,
            status = payment.status.wireValue(),
            fraudScore = payment.fraudScore,
            createdAt = payment.createdAt,
            updatedAt = payment.updatedAt,
        )
    }
}

data class PaymentStatusResponse(val status: String)

data class MerchantResponse(
    val id: UUID,
    val name: String,
    @JsonProperty("account_id") val accountId: UUID,
    @JsonProperty("created_at") val createdAt: Instant,
) {
    companion object {
        // No exponemos api_key_hash: es un secreto de autenticación del comercio, no un dato de lectura.
        fun from(merchant: Merchant): MerchantResponse = MerchantResponse(
            id = merchant.id,
            name = merchant.name,
            accountId = merchant.accountId,
            createdAt = merchant.createdAt,
        )
    }
}

data class ErrorBody(val error: ErrorDetail)
data class ErrorDetail(val code: String, val message: String)
