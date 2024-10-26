package io.cauri.payments.clients

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import java.math.BigDecimal

/**
 * `amount` viaja como string decimal en TODO JSON de la org (las convenciones internas de API: `"1250.50"`, nunca
 * un número). fraud-scoring lo rechaza con 422 si Jackson lo manda como número (bug real
 * encontrado con tráfico real: 2026-09-09). `toPlainString()` evita además la notación
 * científica que `BigDecimal.toString()` puede producir.
 */
class PlainDecimalSerializer : JsonSerializer<BigDecimal>() {
    override fun serialize(value: BigDecimal, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeString(value.toPlainString())
    }
}
