package io.cauri.payments.repo

import io.cauri.payments.domain.Merchant
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MerchantRepository : JpaRepository<Merchant, UUID>
