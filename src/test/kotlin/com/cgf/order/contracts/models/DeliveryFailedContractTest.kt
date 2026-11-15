package com.cgf.order.contracts.models

import org.apache.avro.Schema
import org.apache.avro.io.DecoderFactory
import org.apache.avro.io.EncoderFactory
import org.apache.avro.specific.SpecificDatumReader
import org.apache.avro.specific.SpecificDatumWriter
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A delivery that failed after pickup (TICKET-14.4): the courier reached the address and could not hand the order over.
 * It has its own topic so that no reader can mistake it for a cancellation, which is refunded.
 */
class DeliveryFailedContractTest {

    @Test
    fun `a failed delivery round-trips with its courier, reason, check and time`() {
        val written = DeliveryFailedEvent.newBuilder()
            .setDeliveryId("delivery-1")
            .setOrderId("order-1")
            .setRestaurantId(RESTAURANT_ID)
            .setCustomerId(CUSTOMER_ID)
            .setDriverId(DRIVER_ID)
            .setReason("CUSTOMER_UNREACHABLE")
            .setVerified(false)
            .setFailedAt(FAILED_AT)
            .build()

        val read = roundTrip(written)

        assertEquals(written, read)
        assertEquals(DRIVER_ID, read.driverId)
        assertEquals("CUSTOMER_UNREACHABLE", read.reason)
        assertEquals(false, read.verified)
        assertEquals(FAILED_AT, read.failedAt)
    }

    /** Same rule as the cancellation reason: a new reason must not dead-letter readers that have not upgraded. */
    @Test
    fun `reason is a string, never an enum`() {
        assertEquals(Schema.Type.STRING, DeliveryFailedEvent.getClassSchema().getField("reason").schema().type)
    }

    /** Only a courier holding the order can fail its delivery, so every failure names one. */
    @Test
    fun `the courier and the time are required`() {
        val schema = DeliveryFailedEvent.getClassSchema()

        assertEquals(Schema.Type.STRING, schema.getField("driverId").schema().type)
        assertEquals(Schema.Type.LONG, schema.getField("failedAt").schema().type)
        assertEquals("timestamp-millis", schema.getField("failedAt").schema().logicalType.name)
    }

    private fun roundTrip(event: DeliveryFailedEvent): DeliveryFailedEvent {
        val out = ByteArrayOutputStream()
        val encoder = EncoderFactory.get().binaryEncoder(out, null)
        SpecificDatumWriter<DeliveryFailedEvent>(DeliveryFailedEvent.getClassSchema()).write(event, encoder)
        encoder.flush()
        return SpecificDatumReader<DeliveryFailedEvent>(DeliveryFailedEvent.getClassSchema())
            .read(null, DecoderFactory.get().binaryDecoder(out.toByteArray(), null))
    }

    private companion object {
        val RESTAURANT_ID: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111")
        val CUSTOMER_ID: UUID = UUID.fromString("22222222-2222-2222-2222-222222222222")
        val DRIVER_ID: UUID = UUID.fromString("33333333-3333-3333-3333-333333333333")
        val FAILED_AT: Instant = Instant.parse("2026-11-15T12:30:00.123Z")
    }
}
