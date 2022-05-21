package io.cauri.payments.domain

enum class PaymentStatus {
    PENDING,
    APPROVED,
    REJECTED,
    COMPLETED,
    FAILED;

    /** Matches the lowercase values used by the `payments.status` check constraint and the JSON contract. */
    fun wireValue(): String = name.lowercase()

    companion object {
        fun fromWireValue(value: String): PaymentStatus = valueOf(value.uppercase())
    }
}
