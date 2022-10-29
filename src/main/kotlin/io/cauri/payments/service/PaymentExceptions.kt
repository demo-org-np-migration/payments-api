package io.cauri.payments.service

import java.util.UUID

class PaymentRejectedException(val paymentId: UUID) : RuntimeException("payment_rejected")

class PaymentNotFoundException(val paymentId: UUID) : RuntimeException("payment_not_found")

class MerchantNotFoundException(val merchantId: UUID) : RuntimeException("merchant_not_found")
