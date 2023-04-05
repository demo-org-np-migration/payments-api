package io.cauri.payments.service

import io.cauri.payments.clients.FraudClient
import io.cauri.payments.clients.FraudDecision
import io.cauri.payments.clients.InsufficientFundsException
import io.cauri.payments.clients.LedgerClient
import io.cauri.payments.clients.LedgerEntry
import io.cauri.payments.clients.ScoreResponse
import io.cauri.payments.clients.TransferResponse
import io.cauri.payments.domain.Merchant
import io.cauri.payments.domain.PaymentStatus
import io.cauri.payments.events.PaymentCompletedEvent
import io.cauri.payments.events.PaymentEventPublisher
import io.cauri.payments.repo.MerchantRepository
import io.cauri.payments.repo.PaymentRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional
import java.util.UUID

class PaymentServiceTest {

    private val paymentRepository = mockk<PaymentRepository>()
    private val merchantRepository = mockk<MerchantRepository>()
    private val fraudClient = mockk<FraudClient>()
    private val ledgerClient = mockk<LedgerClient>()
    private val eventPublisher = mockk<PaymentEventPublisher>()

    private lateinit var service: PaymentService

    private val fromAccount = UUID.randomUUID()
    private val toAccount = UUID.randomUUID()
    private val amount = BigDecimal("1250.50")
    private val currency = "ARS"

    @BeforeEach
    fun setUp() {
        service = PaymentService(paymentRepository, merchantRepository, fraudClient, ledgerClient, eventPublisher)
        every { paymentRepository.save(any()) } answers { firstArg() }
    }

    @Test
    fun `approved payment is completed and publishes the kafka event`() {
        every { fraudClient.score(any()) } returns Mono.just(ScoreResponse(BigDecimal("0.100"), FraudDecision.approve, emptyList()))
        every { ledgerClient.transfer(any()) } returns Mono.just(
            TransferResponse(LedgerEntry(id = UUID.randomUUID()), LedgerEntry(id = UUID.randomUUID())),
        )
        every { eventPublisher.publish(any()) } just runs

        val payment = service.createPayment(fromAccount, toAccount, amount, currency, "ref-1")

        assertEquals(PaymentStatus.COMPLETED, payment.status)
        val eventSlot = slot<PaymentCompletedEvent>()
        verify(exactly = 1) { eventPublisher.publish(capture(eventSlot)) }
        assertEquals(payment.id, eventSlot.captured.paymentId)
    }

    @Test
    fun `payment rejected by fraud-scoring never reaches the ledger and never publishes`() {
        every { fraudClient.score(any()) } returns Mono.just(ScoreResponse(BigDecimal("0.950"), FraudDecision.reject, listOf("velocity")))

        val ex = assertThrows(PaymentRejectedException::class.java) {
            service.createPayment(fromAccount, toAccount, amount, currency, "ref-2")
        }

        assertEquals(true, ex.message?.isNotBlank())
        verify(exactly = 0) { ledgerClient.transfer(any()) }
        verify(exactly = 0) { eventPublisher.publish(any()) }
    }

    @Test
    fun `review decision still transfers and keeps the fraud score`() {
        every { fraudClient.score(any()) } returns Mono.just(ScoreResponse(BigDecimal("0.550"), FraudDecision.review, listOf("new_device")))
        every { ledgerClient.transfer(any()) } returns Mono.just(
            TransferResponse(LedgerEntry(id = UUID.randomUUID()), LedgerEntry(id = UUID.randomUUID())),
        )
        every { eventPublisher.publish(any()) } just runs

        val payment = service.createPayment(fromAccount, toAccount, amount, currency, "ref-3")

        assertEquals(PaymentStatus.COMPLETED, payment.status)
        assertEquals(BigDecimal("0.550"), payment.fraudScore)
        verify(exactly = 1) { ledgerClient.transfer(any()) }
    }

    @Test
    fun `insufficient funds from ledger marks the payment failed and does not publish`() {
        every { fraudClient.score(any()) } returns Mono.just(ScoreResponse(BigDecimal("0.100"), FraudDecision.approve, emptyList()))
        every { ledgerClient.transfer(any()) } returns Mono.error(InsufficientFundsException("insufficient_funds"))

        assertThrows(InsufficientFundsException::class.java) {
            service.createPayment(fromAccount, toAccount, amount, currency, "ref-4")
        }

        verify(exactly = 0) { eventPublisher.publish(any()) }
    }

    @Test
    fun `chargeMerchant looks up the merchant and sets merchant_id and to_account`() {
        val merchantId = UUID.randomUUID()
        val merchant = Merchant(merchantId, "Almacén Bruno", toAccount, null, Instant.now())
        every { merchantRepository.findById(merchantId) } returns Optional.of(merchant)
        every { fraudClient.score(any()) } returns Mono.just(ScoreResponse(BigDecimal("0.100"), FraudDecision.approve, emptyList()))
        every { ledgerClient.transfer(any()) } returns Mono.just(
            TransferResponse(LedgerEntry(id = UUID.randomUUID()), LedgerEntry(id = UUID.randomUUID())),
        )
        every { eventPublisher.publish(any()) } just runs

        val payment = service.chargeMerchant(merchantId, fromAccount, amount, currency, "charge-1")

        assertEquals(merchantId, payment.merchantId)
        assertEquals(toAccount, payment.toAccount)
    }

    @Test
    fun `chargeMerchant fails fast when the merchant does not exist`() {
        val merchantId = UUID.randomUUID()
        every { merchantRepository.findById(merchantId) } returns Optional.empty()

        assertThrows(MerchantNotFoundException::class.java) {
            service.chargeMerchant(merchantId, fromAccount, amount, currency, "charge-2")
        }
    }
}
