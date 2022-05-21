package io.cauri.payments.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "payments")
class Payment(
    @Id
    val id: UUID,

    @Column(name = "from_account", nullable = false)
    val fromAccount: UUID,

    @Column(name = "to_account", nullable = false)
    val toAccount: UUID,

    @Column(name = "merchant_id")
    val merchantId: UUID? = null,

    @Column(nullable = false)
    val amount: BigDecimal,

    @Column(nullable = false)
    val currency: String,

    @Column(nullable = false)
    var status: PaymentStatus,

    @Column(name = "fraud_score")
    var fraudScore: BigDecimal? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant,
)
