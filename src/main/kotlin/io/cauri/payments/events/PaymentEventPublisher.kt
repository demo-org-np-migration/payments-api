package io.cauri.payments.events

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

/** Publica `payments.completed` a Kafka. Solo se llama cuando el pago queda `completed`. */
@Component
class PaymentEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val paymentsCompletedTopic: String,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(PaymentEventPublisher::class.java)

    fun publish(event: PaymentCompletedEvent) {
        val payload = objectMapper.writeValueAsString(event)
        kafkaTemplate.send(paymentsCompletedTopic, event.paymentId.toString(), payload)
            .whenComplete { result, ex ->
                if (ex != null) {
                    log.error("failed to publish payments.completed for payment_id={}: {}", event.paymentId, ex.message)
                } else {
                    log.info(
                        "published payments.completed payment_id={} topic={} offset={}",
                        event.paymentId, paymentsCompletedTopic, result?.recordMetadata?.offset(),
                    )
                }
            }
    }
}
