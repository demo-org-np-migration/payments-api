package io.cauri.payments.api

import io.cauri.payments.service.PaymentService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class MerchantsController(private val paymentService: PaymentService) {

    @GetMapping("/v1/merchants/{id}")
    fun get(@PathVariable id: UUID): MerchantResponse =
        MerchantResponse.from(paymentService.getMerchant(id))

    @PreAuthorize("hasRole('merchant') or hasRole('service')")
    @PostMapping("/v1/merchants/{id}/charges")
    fun charge(@PathVariable id: UUID, @Valid @RequestBody request: ChargeRequest): ResponseEntity<PaymentResponse> {
        val payment = paymentService.chargeMerchant(
            merchantId = id,
            fromAccount = request.from_account,
            amount = request.amount,
            currency = request.currency,
            reference = request.reference,
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(PaymentResponse.from(payment))
    }
}
