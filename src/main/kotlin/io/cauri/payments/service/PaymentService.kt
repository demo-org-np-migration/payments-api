package io.cauri.payments.service

import io.cauri.payments.clients.FraudClient
import io.cauri.payments.clients.FraudDecision
import io.cauri.payments.clients.InsufficientFundsException
import io.cauri.payments.clients.LedgerClient
import io.cauri.payments.clients.ScoreRequest
import io.cauri.payments.clients.TransferRequest
import io.cauri.payments.domain.Payment
import io.cauri.payments.domain.PaymentStatus
import io.cauri.payments.events.PaymentCompletedEvent
import io.cauri.payments.events.PaymentEventPublisher
import io.cauri.payments.repo.MerchantRepository
import io.cauri.payments.repo.PaymentRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Orquesta el flujo de un pago (las convenciones internas de API, spec payments-api "Contrato"):
 * 1. Persistir `pending`.
 * 2. fraud-scoring `POST /v1/score`. `reject` -> `rejected` + 422 payment_rejected.
 *    `review` -> se aprueba igual, pero se guarda `fraud_score`.
 * 3. ledger-core `POST /v1/ledger/transfers`. 422 del ledger -> `failed` + 422 insufficient_funds.
 * 4. `completed`, publicar `payments.completed`.
 */
@Service
class PaymentService(
    private val paymentRepository: PaymentRepository,
    private val merchantRepository: MerchantRepository,
    private val fraudClient: FraudClient,
    private val ledgerClient: LedgerClient,
    private val eventPublisher: PaymentEventPublisher,
) {
    private val log = LoggerFactory.getLogger(PaymentService::class.java)

    fun createPayment(
        fromAccount: UUID,
        toAccount: UUID,
        amount: BigDecimal,
        currency: String,
        reference: String,
        merchantId: UUID? = null,
    ): Payment {
        val now = Instant.now()
        var payment = Payment(
            id = UUID.randomUUID(),
            fromAccount = fromAccount,
            toAccount = toAccount,
            merchantId = merchantId,
            amount = amount,
            currency = currency,
            status = PaymentStatus.PENDING,
            createdAt = now,
            updatedAt = now,
        )
        payment = paymentRepository.save(payment)

        val scoreResponse = fraudClient.score(
            ScoreRequest(
                payment_id = payment.id,
                from_account = fromAccount,
                to_account = toAccount,
                amount = amount,
                currency = currency,
            ),
        ).block()!!

        payment.fraudScore = scoreResponse.score
        payment.updatedAt = Instant.now()

        if (scoreResponse.decision == FraudDecision.reject) {
            payment.status = PaymentStatus.REJECTED
            paymentRepository.save(payment)
            log.info("payment {} rejected by fraud-scoring, signals={}", payment.id, scoreResponse.signals)
            throw PaymentRejectedException(payment.id)
        }

        // approve o review: se aprueba igual, la diferencia ya quedó registrada en fraud_score.
        payment.status = PaymentStatus.APPROVED
        paymentRepository.save(payment)

        try {
            ledgerClient.transfer(
                TransferRequest(
                    from_account = fromAccount,
                    to_account = toAccount,
                    amount = amount,
                    currency = currency,
                    reference = reference,
                ),
            ).block()
        } catch (ex: InsufficientFundsException) {
            payment.status = PaymentStatus.FAILED
            payment.updatedAt = Instant.now()
            paymentRepository.save(payment)
            log.warn("payment {} failed, insufficient funds", payment.id)
            throw ex
        }

        payment.status = PaymentStatus.COMPLETED
        payment.updatedAt = Instant.now()
        payment = paymentRepository.save(payment)

        eventPublisher.publish(
            PaymentCompletedEvent.of(
                paymentId = payment.id,
                fromAccount = payment.fromAccount,
                toAccount = payment.toAccount,
                merchantId = payment.merchantId,
                amount = payment.amount,
                currency = payment.currency,
            ),
        )

        return payment
    }

    fun chargeMerchant(
        merchantId: UUID,
        fromAccount: UUID,
        amount: BigDecimal,
        currency: String,
        reference: String,
    ): Payment {
        val merchant = merchantRepository.findById(merchantId)
            .orElseThrow { MerchantNotFoundException(merchantId) }
        return createPayment(
            fromAccount = fromAccount,
            toAccount = merchant.accountId,
            amount = amount,
            currency = currency,
            reference = reference,
            merchantId = merchant.id,
        )
    }

    fun getPayment(id: UUID): Payment =
        paymentRepository.findById(id).orElseThrow { PaymentNotFoundException(id) }

    fun listPayments(accountId: UUID): List<Payment> =
        paymentRepository.findByFromAccountOrToAccount(accountId, accountId)

    fun getMerchant(id: UUID) =
        merchantRepository.findById(id).orElseThrow { MerchantNotFoundException(id) }
}
