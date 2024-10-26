package io.cauri.payments.clients

import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient
import java.math.BigDecimal
import java.util.UUID

/**
 * Bug real con tráfico real (2026-09-09): fraud-scoring devolvía 422 "Input should be a valid
 * string" porque `amount` viajaba como número JSON en vez de string decimal (las convenciones internas de API).
 * Este test fija el contrato de bytes que sale por la red, no solo el tipo Kotlin.
 */
class FraudClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: FraudClient

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        val webClient = WebClient.builder().baseUrl(server.url("/").toString()).build()
        client = FraudClient(webClient)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `amount travels as a decimal string to fraud-scoring, not a JSON number`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"score":0.100,"decision":"approve","signals":[]}"""),
        )

        val paymentId = UUID.randomUUID()
        val fromAccount = UUID.randomUUID()
        val toAccount = UUID.randomUUID()

        val result = client.score(
            ScoreRequest(
                payment_id = paymentId,
                from_account = fromAccount,
                to_account = toAccount,
                amount = BigDecimal("125.50"),
                currency = "ARS",
            ),
        ).block()

        assertEquals(FraudDecision.approve, result?.decision)

        val recorded = server.takeRequest()
        val rawBody = recorded.body.readUtf8()
        val json = ObjectMapper().readTree(rawBody)

        assertTrue(json.get("amount").isTextual, "amount debe viajar como string, no como número JSON: $rawBody")
        assertEquals("125.50", json.get("amount").asText())
        assertEquals(paymentId.toString(), json.get("payment_id").asText())
        assertEquals(fromAccount.toString(), json.get("from_account").asText())
        assertEquals(toAccount.toString(), json.get("to_account").asText())
        assertEquals("ARS", json.get("currency").asText())
    }
}
