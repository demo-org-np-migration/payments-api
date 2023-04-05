package io.cauri.payments.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.cauri.payments.domain.Payment
import io.cauri.payments.domain.PaymentStatus
import io.cauri.payments.service.PaymentService
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@WebMvcTest(PaymentsController::class)
class PaymentsControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @MockkBean
    lateinit var paymentService: PaymentService

    // El resource server necesita un JwtDecoder en el contexto; en el test lo reemplazamos para
    // no pegarle a Keycloak. La autenticación real la inyecta el request post-processor `jwt()`.
    @MockkBean
    lateinit var jwtDecoder: JwtDecoder

    private fun samplePayment(id: UUID, status: PaymentStatus = PaymentStatus.COMPLETED): Payment {
        val now = Instant.parse("2026-09-09T12:00:00Z")
        return Payment(
            id = id,
            fromAccount = UUID.randomUUID(),
            toAccount = UUID.randomUUID(),
            merchantId = null,
            amount = BigDecimal("1250.50"),
            currency = "ARS",
            status = status,
            fraudScore = BigDecimal("0.100"),
            createdAt = now,
            updatedAt = now,
        )
    }

    @Test
    fun `deprecated status endpoint returns deprecation headers and status body`() {
        val id = UUID.randomUUID()
        every { paymentService.getPayment(id) } returns samplePayment(id)

        mockMvc.perform(get("/v1/payments/$id/status").with(jwt()))
            .andExpect(status().isOk)
            .andExpect(header().string("Deprecation", "true"))
            .andExpect(header().string("Sunset", "Thu, 01 Oct 2026 00:00:00 GMT"))
            .andExpect(header().string(HttpHeaders.LINK, "</v1/payments/$id>; rel=\"successor-version\""))
            .andExpect(jsonPath("$.status").value("completed"))
    }

    @Test
    fun `status endpoint requires authentication`() {
        val id = UUID.randomUUID()
        mockMvc.perform(get("/v1/payments/$id/status"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `create payment returns 201 with the created payment`() {
        val id = UUID.randomUUID()
        val fromAccount = UUID.randomUUID()
        val toAccount = UUID.randomUUID()
        every {
            paymentService.createPayment(fromAccount, toAccount, BigDecimal("1250.50"), "ARS", "ref-1")
        } returns samplePayment(id).let {
            Payment(id, fromAccount, toAccount, null, BigDecimal("1250.50"), "ARS", PaymentStatus.COMPLETED, BigDecimal("0.100"), it.createdAt, it.updatedAt)
        }

        val body = """{"from_account":"$fromAccount","to_account":"$toAccount","amount":1250.50,"currency":"ARS","reference":"ref-1"}"""

        mockMvc.perform(
            post("/v1/payments")
                .with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.status").value("completed"))
            .andExpect(jsonPath("$.currency").value("ARS"))
    }

    @Test
    fun `get payment returns the payment by id`() {
        val id = UUID.randomUUID()
        every { paymentService.getPayment(id) } returns samplePayment(id)

        mockMvc.perform(get("/v1/payments/$id").with(jwt()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(id.toString()))
    }
}
