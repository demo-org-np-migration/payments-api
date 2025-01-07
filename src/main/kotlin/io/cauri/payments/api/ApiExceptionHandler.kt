package io.cauri.payments.api

import io.cauri.payments.clients.InsufficientFundsException
import io.cauri.payments.service.MerchantNotFoundException
import io.cauri.payments.service.PaymentNotFoundException
import io.cauri.payments.service.PaymentRejectedException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.reactive.function.client.WebClientResponseException

/** Errores en el formato de las convenciones internas de API: `{"error": {"code": "<snake_case>", "message": "<texto>"}}`. */
@RestControllerAdvice
class ApiExceptionHandler {

    private val log = LoggerFactory.getLogger(ApiExceptionHandler::class.java)

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

    /** Un path variable que no parsea como UUID (ej. `GET /v1/payments/null/status`) hoy caía en el body default de Spring. */
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun onTypeMismatch(ex: MethodArgumentTypeMismatchException): ResponseEntity<ErrorBody> =
        error(HttpStatus.BAD_REQUEST, "invalid_id", "${ex.name} is not a valid ${ex.requiredType?.simpleName ?: "value"}: ${ex.value}")

    /** ledger-core o fraud-scoring devolvieron algo que no manejamos explícitamente (no un 422 esperado). */
    @ExceptionHandler(WebClientResponseException::class)
    fun onUpstreamError(ex: WebClientResponseException): ResponseEntity<ErrorBody> {
        log.error("unhandled upstream error {} {}: {}", ex.request?.method, ex.request?.uri, ex.responseBodyAsString)
        return error(HttpStatus.BAD_GATEWAY, "upstream_error", "upstream call failed with ${ex.statusCode.value()}")
    }

    private fun error(status: HttpStatus, code: String, message: String) =
        ResponseEntity.status(status).body(ErrorBody(ErrorDetail(code, message)))
}
