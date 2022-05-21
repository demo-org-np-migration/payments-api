package io.cauri.payments.repo

import io.cauri.payments.domain.Payment
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PaymentRepository : JpaRepository<Payment, UUID> {
    fun findByFromAccountOrToAccount(fromAccount: UUID, toAccount: UUID): List<Payment>
}
