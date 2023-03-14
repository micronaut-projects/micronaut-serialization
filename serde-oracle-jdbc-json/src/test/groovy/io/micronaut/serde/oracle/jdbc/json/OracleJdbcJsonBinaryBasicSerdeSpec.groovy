package io.micronaut.serde.oracle.jdbc.json

import io.micronaut.core.type.Argument
import io.micronaut.json.JsonMapper
import io.micronaut.serde.AbstractBasicSerdeSpec
import io.micronaut.serde.bson.Address
import io.micronaut.serde.bson.SampleData
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import oracle.jdbc.driver.json.tree.OracleJsonArrayImpl
import oracle.jdbc.driver.json.tree.OracleJsonBinaryImpl
import oracle.jdbc.driver.json.tree.OracleJsonDateImpl
import oracle.jdbc.driver.json.tree.OracleJsonDoubleImpl
import oracle.jdbc.driver.json.tree.OracleJsonIntervalDSImpl
import oracle.jdbc.driver.json.tree.OracleJsonIntervalYMImpl
import oracle.jdbc.driver.json.tree.OracleJsonStringImpl
import oracle.jdbc.driver.json.tree.OracleJsonTimestampImpl
import oracle.jdbc.driver.json.tree.OracleJsonTimestampTZImpl
import oracle.sql.json.OracleJsonFactory
import oracle.sql.json.OracleJsonObject

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.Period

@MicronautTest
class OracleJdbcJsonBinaryBasicSerdeSpec extends AbstractBasicSerdeSpec {

    @Inject
    OracleJdbcJsonBinaryObjectMapper osonMapper

    @Inject
    OracleJdbcJsonTextObjectMapper textJsonMapper

    @Override
    JsonMapper getJsonMapper() {
        return osonMapper
    }

    @Override
    String writeJson(JsonMapper jsonMapper, Object bean) {
        def bytes = jsonMapper.writeValueAsBytes(bean)
        def object = osonMapper.readValue(bytes, OracleJsonObject)
        return new String(textJsonMapper.writeValueAsBytes(object), StandardCharsets.UTF_8)
    }

    @Override
    byte[] jsonAsBytes(String json) {
        def object = textJsonMapper.readValue(json, OracleJsonObject)
        return osonMapper.writeValueAsBytes(object)
    }

    boolean objRepresentationMatches(Object obj, String json) {
        def expected = textJsonMapper.readValue(json, Argument.of(obj.getClass()))
        assert obj == expected
        obj == expected
    }

    void 'test parsing various types'() {
        given:
        def etag = UUID.randomUUID().toString()
        def memo = "some long content"
        def uuid = UUID.randomUUID()
        def duration = Duration.ofMinutes(15)
        def period = Period.of(2, 3, 0)

        def localDateTime = LocalDateTime.now()
        def offsetDateTime = OffsetDateTime.now()

        def address = new Address("1", "Main St", "Someville", "11122")

        // simple props
        def description = "data description"
        int grade = 9
        double rating = 2.4
        def rates = List.of(109.5f, 107.0f, 111.85f)
        def active = true

        def jsonFactory = new OracleJsonFactory()
        def oson = jsonFactory.createObject()

        // Add manually value to the object to mimic how Oracle DB would return it
        oson.put("etag", new OracleJsonBinaryImpl(etag.getBytes(Charset.defaultCharset()), false))
        oson.put("memo", new OracleJsonBinaryImpl(memo.getBytes(Charset.defaultCharset()), false))
        oson.put("uuid", new OracleJsonStringImpl(uuid.toString()))
        oson.put("duration", new OracleJsonIntervalDSImpl(duration))
        oson.put("period", new OracleJsonIntervalYMImpl(period))
        oson.put("localDateTime", new OracleJsonTimestampImpl(localDateTime))
        oson.put("offsetDateTime", new OracleJsonTimestampTZImpl(offsetDateTime))
        oson.put("date", new OracleJsonDateImpl(localDateTime))
        oson.put("description", new OracleJsonStringImpl(description))
        oson.put("grade", new OracleJsonDoubleImpl(grade))
        oson.put("rating", new OracleJsonDoubleImpl(rating))
        def oracleJsonArrayRates = new OracleJsonArrayImpl()
        rates.forEach {oracleJsonArrayRates.add(it.doubleValue())}
        oson.put("rates", oracleJsonArrayRates)
        def oracleJsonObjectAddress = jsonFactory.createObject()
        oracleJsonObjectAddress.put("address", address.getAddress())
        oracleJsonObjectAddress.put("street", address.getStreet())
        oracleJsonObjectAddress.put("postcode", address.getPostcode())
        oracleJsonObjectAddress.put("town", address.getTown())
        oson.put("address", oracleJsonObjectAddress)
        oson.put("active", new OracleJsonStringImpl(active.toString()))

        def bytes = osonMapper.writeValueAsBytes(oson)
        when:
        def sampleData = osonMapper.readValue(bytes, SampleData)
        then:
        sampleData.etag == OracleJsonBinaryImpl.getString(etag.getBytes(Charset.defaultCharset()), false)
        sampleData.memo == memo.getBytes(Charset.defaultCharset())
        sampleData.uuid == uuid
        sampleData.duration == duration
        sampleData.period == period
        sampleData.localDateTime == localDateTime
        sampleData.offsetDateTime == offsetDateTime
        sampleData.date == localDateTime.toLocalDate()
        sampleData.description == description
        sampleData.grade == grade
        sampleData.rating == rating
        sampleData.rates == rates
        sampleData.address == address
        !sampleData.person
        sampleData.active == active
        when:
        def json = textJsonMapper.writeValueAsString(sampleData)
        then:
        json != ''
        // Just simple validation, no need to parse
        json.contains("\"etag\":\"" + OracleJsonBinaryImpl.getString(etag.getBytes(Charset.defaultCharset()), false) + "\"")
    }

}
