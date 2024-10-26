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

class LedgerClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: LedgerClient

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        val webClient = WebClient.builder().baseUrl(server.url("/").toString()).build()
        client = LedgerClient(webClient)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `amount travels as a decimal string to ledger-core transfers`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"debit_entry":{"id":"${UUID.randomUUID()}"},"credit_entry":{"id":"${UUID.randomUUID()}"}}""",
                ),
        )

        val fromAccount = UUID.randomUUID()
        val toAccount = UUID.randomUUID()

        client.transfer(
            TransferRequest(
                from_account = fromAccount,
                to_account = toAccount,
                amount = BigDecimal("1250.50"),
                currency = "ARS",
                reference = "ref-1",
            ),
        ).block()

        val recorded = server.takeRequest()
        val json = ObjectMapper().readTree(recorded.body.readUtf8())

        assertTrue(json.get("amount").isTextual, "amount debe viajar como string, no como número JSON")
        assertEquals("1250.50", json.get("amount").asText())
        assertEquals(fromAccount.toString(), json.get("from_account").asText())
        assertEquals(toAccount.toString(), json.get("to_account").asText())
        assertEquals("ARS", json.get("currency").asText())
        assertEquals("ref-1", json.get("reference").asText())
    }
}
