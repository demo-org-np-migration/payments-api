package io.cauri.payments.config

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

@Configuration
class WebClientConfig(private val tokenService: KeycloakTokenService) {

    private val log = LoggerFactory.getLogger(WebClientConfig::class.java)

    /** Agrega `Authorization: Bearer <token de servicio>` a cada request saliente (las convenciones internas de API). */
    private fun bearerTokenFilter(): ExchangeFilterFunction =
        ExchangeFilterFunction.ofRequestProcessor { request ->
            tokenService.getAccessToken().map { token ->
                ClientRequest.from(request)
                    .headers { headers -> headers.set(HttpHeaders.AUTHORIZATION, "Bearer $token") }
                    .build()
            }
        }

    /**
     * Un 4xx/5xx que el cliente no maneja explícitamente (por ejemplo un 422 de ledger-core sí se
     * maneja como insufficient_funds, pero cualquier otra cosa hoy termina como 500 mudo). Logueamos
     * status y body a nivel ERROR antes de dejar que la excepción siga su curso, para no laburar a
     * ciegas cuando algo cambia río arriba.
     */
    private fun errorLoggingFilter(clientName: String): ExchangeFilterFunction =
        ExchangeFilterFunction.ofResponseProcessor { response ->
            if (response.statusCode().isError) {
                response.bodyToMono(String::class.java)
                    .defaultIfEmpty("")
                    .map { body ->
                        log.error("{} respondió {}: {}", clientName, response.statusCode().value(), body)
                        response.mutate().body(body).build()
                    }
            } else {
                Mono.just(response)
            }
        }

    @Bean
    fun ledgerWebClient(@Value("\${clients.ledger-core.base-url}") baseUrl: String): WebClient =
        WebClient.builder()
            .baseUrl(baseUrl)
            .filter(bearerTokenFilter())
            .filter(errorLoggingFilter("ledger-core"))
            .build()

    @Bean
    fun fraudWebClient(@Value("\${clients.fraud-scoring.base-url}") baseUrl: String): WebClient =
        WebClient.builder()
            .baseUrl(baseUrl)
            .filter(bearerTokenFilter())
            .filter(errorLoggingFilter("fraud-scoring"))
            .build()
}
