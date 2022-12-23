package io.cauri.payments.api

import io.cauri.payments.service.PaymentService
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/v1/payments")
class PaymentsController(private val paymentService: PaymentService) {

    private val log = LoggerFactory.getLogger(PaymentsController::class.java)

    @PostMapping
    fun create(@Valid @RequestBody request: CreatePaymentRequest): ResponseEntity<PaymentResponse> {
        val payment = paymentService.createPayment(
            fromAccount = request.from_account,
            toAccount = request.to_account,
            amount = request.amount,
            currency = request.currency,
            reference = request.reference,
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(PaymentResponse.from(payment))
    }

    @GetMapping("/{id}")
    fun get(@PathVariable id: UUID): PaymentResponse =
        PaymentResponse.from(paymentService.getPayment(id))

    @GetMapping
    fun list(@RequestParam("account_id") accountId: UUID): List<PaymentResponse> =
        paymentService.listPayments(accountId).map(PaymentResponse::from)

    /**
     * Deprecado (spec payments-api "Contrato"): se elimina el 2026-10-01. `mobile-bff` sigue
     * llamando a este endpoint para `GET /mobile/transfers/{id}/status` — ver README#deprecations
     * (defecto D5). Devuelve `{status}` con los headers estándar de deprecación HTTP.
     */
    @Deprecated("usar GET /v1/payments/{id}; se elimina 2026-10-01")
    @GetMapping("/{id}/status")
    fun getStatus(@PathVariable id: UUID): ResponseEntity<PaymentStatusResponse> {
        log.warn("deprecated endpoint called: GET /v1/payments/{}/status, sunset=2026-10-01", id)
        val payment = paymentService.getPayment(id)
        return ResponseEntity.ok()
            .header("Deprecation", "true")
            .header("Sunset", "Thu, 01 Oct 2026 00:00:00 GMT")
            .header(HttpHeaders.LINK, "</v1/payments/$id>; rel=\"successor-version\"")
            .body(PaymentStatusResponse(payment.status.wireValue()))
    }
}
