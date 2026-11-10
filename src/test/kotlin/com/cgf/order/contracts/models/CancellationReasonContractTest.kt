package com.cgf.order.contracts.models

import org.apache.avro.JsonProperties
import org.apache.avro.Schema
import org.apache.avro.generic.GenericData
import org.apache.avro.generic.GenericDatumReader
import org.apache.avro.generic.GenericDatumWriter
import org.apache.avro.generic.GenericRecord
import org.apache.avro.io.DecoderFactory
import org.apache.avro.io.EncoderFactory
import org.apache.avro.specific.SpecificDatumReader
import org.apache.avro.specific.SpecificDatumWriter
import org.apache.avro.specific.SpecificRecord
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `reason` says why a delivery or an order was cancelled. It is added to events that services already read, so the
 * tests pin the three things that make that safe: readers that predate it survive, writers that predate it read as
 * "no reason", and the field is a string, never an enum.
 */
class CancellationReasonContractTest {

    private val cancellationEvents: List<Schema> = listOf(
        DeliveryCancelledEvent.getClassSchema(),
        OrderCancelledEvent.getClassSchema(),
    )

    /**
     * A string, never an enum: an enum would make every new reason a contract change, because a consumer that has not
     * upgraded reads the unknown symbol as its fallback and `RejectEnumFallbackValidator` dead-letters the record.
     */
    @Test
    fun `reason is an optional string that defaults to null`() {
        cancellationEvents.forEach { record ->
            val field = record.getField("reason")
            assertTrue(field != null, "${record.name} has no reason field")
            assertEquals(listOf(Schema.Type.NULL, Schema.Type.STRING), field.schema().types.map { it.type }, record.name)
            assertTrue(field.defaultVal() == JsonProperties.NULL_VALUE, "${record.name}: reason must default to null")
        }
    }

    @Test
    fun `a delivery cancellation carries its reason`() {
        val written = DeliveryCancelledEvent.newBuilder()
            .setDeliveryId("delivery-1")
            .setOrderId("order-1")
            .setRestaurantId(RESTAURANT_ID)
            .setCustomerId(CUSTOMER_ID)
            .setReason("NO_COURIER")
            .build()

        val read = roundTrip(written, DeliveryCancelledEvent.getClassSchema(), DeliveryCancelledEvent.getClassSchema())

        assertEquals("NO_COURIER", read.reason)
    }

    @Test
    fun `an order cancellation carries its reason`() {
        val written = OrderCancelledEvent.newBuilder()
            .setOrderId("order-1")
            .setRestaurantId(RESTAURANT_ID)
            .setCustomerId(CUSTOMER_ID)
            .setStatus(OrderStatus.CANCELED)
            .setReason("NO_COURIER")
            .build()

        val read = roundTrip(written, OrderCancelledEvent.getClassSchema(), OrderCancelledEvent.getClassSchema())

        assertEquals("NO_COURIER", read.reason)
    }

    @Test
    fun `a cancellation written before reason existed reads back with no reason`() {
        cancellationEvents.forEach { reader ->
            val writer = withoutReason(reader)
            val record = GenericData.Record(writer).apply {
                writer.fields.forEach { put(it.name(), sampleValue(it)) }
            }

            val read = SpecificDatumReader<SpecificRecord>(writer, reader)
                .read(null, DecoderFactory.get().binaryDecoder(encode(writer, record), null))

            assertNull(read.get(reader.getField("reason").pos()), reader.name)
        }
    }

    @Test
    fun `a consumer that predates reason still reads a cancellation that carries one`() {
        cancellationEvents.forEach { writer ->
            val reader = withoutReason(writer)
            val record = GenericData.Record(writer).apply {
                writer.fields.forEach { put(it.name(), sampleValue(it)) }
                put("reason", "NO_COURIER")
            }

            val read = GenericDatumReader<GenericRecord>(writer, reader)
                .read(null, DecoderFactory.get().binaryDecoder(encode(writer, record), null))

            assertEquals("order-1", read.get("orderId").toString(), writer.name)
            assertNull(reader.getField("reason"), writer.name)
        }
    }

    private fun <T : SpecificRecord> roundTrip(event: T, writer: Schema, reader: Schema): T {
        val out = ByteArrayOutputStream()
        val encoder = EncoderFactory.get().binaryEncoder(out, null)
        SpecificDatumWriter<T>(writer).write(event, encoder)
        encoder.flush()
        return SpecificDatumReader<T>(writer, reader).read(null, DecoderFactory.get().binaryDecoder(out.toByteArray(), null))
    }

    /** The same record as [record], as an earlier contract wrote it: without `reason`. */
    private fun withoutReason(record: Schema): Schema {
        val fields = record.fields
            .filter { it.name() != "reason" }
            .map { Schema.Field(it.name(), it.schema(), it.doc(), it.defaultVal()) }
        return Schema.createRecord(record.name, record.doc, record.namespace, false, fields)
    }

    private fun sampleValue(field: Schema.Field): Any? = when (field.name()) {
        "restaurantId" -> RESTAURANT_ID.toString()
        "customerId" -> CUSTOMER_ID.toString()
        "status" -> GenericData.EnumSymbol(OrderStatus.getClassSchema(), "CANCELED")
        "driverId", "reason" -> null
        else -> "order-1"
    }

    private fun encode(schema: Schema, record: GenericRecord): ByteArray {
        val out = ByteArrayOutputStream()
        val encoder = EncoderFactory.get().binaryEncoder(out, null)
        GenericDatumWriter<GenericRecord>(schema).write(record, encoder)
        encoder.flush()
        return out.toByteArray()
    }

    private companion object {
        val RESTAURANT_ID: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111")
        val CUSTOMER_ID: UUID = UUID.fromString("22222222-2222-2222-2222-222222222222")
    }
}
