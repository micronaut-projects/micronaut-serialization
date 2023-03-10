package io.micronaut.serde.oracle.jdbc.json

import io.micronaut.core.type.Argument
import io.micronaut.json.JsonMapper
import io.micronaut.serde.AbstractBasicSerdeSpec
import io.micronaut.serde.bson.SampleData
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import oracle.jdbc.driver.json.tree.OracleJsonBinaryImpl
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
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.Period
import java.time.ZoneId

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
        def uuid = UUID.randomUUID()
        def duration = Duration.ofMinutes(15)
        def period = Period.of(2, 3, 0)

        def zoneId = ZoneId.systemDefault()
        def instant = Instant.now()
        def localDateTime = LocalDateTime.ofInstant(instant, zoneId)
        def offsetDateTime = OffsetDateTime.ofInstant(instant, zoneId)
        def date = Date.from(instant)

        def jsonFactory = new OracleJsonFactory()
        def oson = jsonFactory.createObject()

        // Add manually value to the object to mimic how Oracle DB would return it
        oson.put("etag", new OracleJsonStringImpl(etag))
        oson.put("uuid", new OracleJsonStringImpl(uuid.toString()))
        oson.put("duration", new OracleJsonIntervalDSImpl(duration))
        oson.put("period", new OracleJsonIntervalYMImpl(period))
        oson.put("localDateTime", new OracleJsonTimestampImpl(localDateTime))
        oson.put("instant", new OracleJsonTimestampImpl(localDateTime))
        oson.put("offsetDateTime", new OracleJsonTimestampTZImpl(offsetDateTime))
        oson.put("date", new OracleJsonTimestampImpl(localDateTime))

        def bytes = osonMapper.writeValueAsBytes(oson)
        when:
        def sampleData = osonMapper.readValue(bytes, SampleData)
        then:
        sampleData.etag == etag
        sampleData.uuid == uuid
        sampleData.duration == duration
        sampleData.period == period
        sampleData.localDateTime == localDateTime
        sampleData.instant == instant
        sampleData.offsetDateTime == offsetDateTime
        sampleData.date == date
        when:
        def json = textJsonMapper.writeValueAsString(sampleData)
        then:
        json != ''
        // Just simple validation, no need to parse
        json.contains("\"etag\":\"" + etag + "\"")
    }

}
