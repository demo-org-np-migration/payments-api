package io.cauri.payments.domain

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

@Converter(autoApply = true)
class PaymentStatusConverter : AttributeConverter<PaymentStatus, String> {
    override fun convertToDatabaseColumn(attribute: PaymentStatus?): String? = attribute?.wireValue()
    override fun convertToEntityAttribute(dbData: String?): PaymentStatus? = dbData?.let { PaymentStatus.fromWireValue(it) }
}
