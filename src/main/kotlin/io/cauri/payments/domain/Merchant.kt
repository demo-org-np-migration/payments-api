package io.cauri.payments.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "merchants")
class Merchant(
    @Id
    val id: UUID,

    @Column(nullable = false)
    val name: String,

    @Column(name = "account_id", nullable = false)
    val accountId: UUID,

    @Column(name = "api_key_hash")
    val apiKeyHash: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
)
