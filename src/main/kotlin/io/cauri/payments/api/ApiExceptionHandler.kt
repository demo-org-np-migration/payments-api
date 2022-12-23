package io.cauri.payments.api

import io.cauri.payments.clients.InsufficientFundsException
import io.cauri.payments.service.MerchantNotFoundException
import io.cauri.payments.service.PaymentNotFoundException
import io.cauri.payments.service.PaymentRejectedException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/** Errores en el formato de las convenciones internas de API: `{"error": {"code": "<snake_case>", "message": "<texto>"}}`. */
@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(PaymentRejectedException::class)
    fun onPaymentRejected(ex: PaymentRejectedException): ResponseEntity<ErrorBody> =
        error(HttpStatus.UNPROCESSABLE_ENTITY, "payment_rejected", "payment ${ex.paymentId} was rejected by fraud-scoring")

    @ExceptionHandler(InsufficientFundsException::class)
    fun onInsufficientFunds(ex: InsufficientFundsException): ResponseEntity<ErrorBody> =
        error(HttpStatus.UNPROCESSABLE_ENTITY, "insufficient_funds", ex.message ?: "insufficient_funds")

    @ExceptionHandler(PaymentNotFoundException::class)
    fun onPaymentNotFound(ex: PaymentNotFoundException): ResponseEntity<ErrorBody> =
        error(HttpStatus.NOT_FOUND, "payment_not_found", "payment ${ex.paymentId} does not exist")

    @ExceptionHandler(MerchantNotFoundException::class)
    fun onMerchantNotFound(ex: MerchantNotFoundException): ResponseEntity<ErrorBody> =
        error(HttpStatus.NOT_FOUND, "merchant_not_found", "merchant ${ex.merchantId} does not exist")

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun onValidation(ex: MethodArgumentNotValidException): ResponseEntity<ErrorBody> =
        error(HttpStatus.BAD_REQUEST, "invalid_request", ex.bindingResult.fieldErrors.joinToString("; ") { "${it.field}: ${it.defaultMessage}" })

    private fun error(status: HttpStatus, code: String, message: String) =
        ResponseEntity.status(status).body(ErrorBody(ErrorDetail(code, message)))
}
