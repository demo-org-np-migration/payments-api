package io.cauri.payments.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter
import org.springframework.security.web.SecurityFilterChain

/**
 * Resource server: valida los JWT que emite Keycloak (issuer = KEYCLOAK_ISSUER, JWKS resuelto por
 * spring-boot-starter-oauth2-resource-server via `issuer-uri`). Los roles vienen en el claim
 * `realm_access.roles` y se mapean a `ROLE_<rol>` (las convenciones internas de API), así los `@PreAuthorize`/
 * `hasRole(...)` de los controllers funcionan con la convención estándar de Spring Security.
 */
@Configuration
@EnableMethodSecurity
class SecurityConfig {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http {
            csrf { disable() }
            sessionManagement { sessionCreationPolicy = SessionCreationPolicy.STATELESS }
            authorizeHttpRequests {
                authorize("/actuator/health", permitAll)
                authorize("/actuator/prometheus", permitAll)
                authorize(anyRequest, authenticated)
            }
            oauth2ResourceServer {
                jwt {
                    jwtAuthenticationConverter = keycloakJwtAuthenticationConverter()
                }
            }
        }
        return http.build()
    }

    private fun keycloakJwtAuthenticationConverter(): JwtAuthenticationConverter {
        val defaultConverter = JwtGrantedAuthoritiesConverter()
        val converter = JwtAuthenticationConverter()
        converter.setJwtGrantedAuthoritiesConverter { jwt ->
            val defaultAuthorities = defaultConverter.convert(jwt) ?: emptyList()
            defaultAuthorities + realmRoleAuthorities(jwt)
        }
        return converter
    }

    @Suppress("UNCHECKED_CAST")
    private fun realmRoleAuthorities(jwt: Jwt): List<GrantedAuthority> {
        val realmAccess = jwt.claims["realm_access"] as? Map<String, Any> ?: return emptyList()
        val roles = realmAccess["roles"] as? Collection<String> ?: return emptyList()
        return roles.map { SimpleGrantedAuthority("ROLE_${it}") }
    }
}
