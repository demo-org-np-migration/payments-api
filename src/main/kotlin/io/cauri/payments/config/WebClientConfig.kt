package io.cauri.payments.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class WebClientConfig(private val tokenService: KeycloakTokenService) {

    /** Agrega `Authorization: Bearer <token de servicio>` a cada request saliente (las convenciones internas de API). */
    private fun bearerTokenFilter(): ExchangeFilterFunction =
        ExchangeFilterFunction.ofRequestProcessor { request ->
            tokenService.getAccessToken().map { token ->
                ClientRequest.from(request)
                    .headers { headers -> headers.set(HttpHeaders.AUTHORIZATION, "Bearer $token") }
                    .build()
            }
        }

    @Bean
    fun ledgerWebClient(@Value("\${clients.ledger-core.base-url}") baseUrl: String): WebClient =
        WebClient.builder()
            .baseUrl(baseUrl)
            .filter(bearerTokenFilter())
            .build()

    @Bean
    fun fraudWebClient(@Value("\${clients.fraud-scoring.base-url}") baseUrl: String): WebClient =
        WebClient.builder()
            .baseUrl(baseUrl)
            .filter(bearerTokenFilter())
            .build()
}
