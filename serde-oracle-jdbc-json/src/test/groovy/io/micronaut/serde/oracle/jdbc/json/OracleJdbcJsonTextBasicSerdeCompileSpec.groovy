package io.micronaut.serde.oracle.jdbc.json

import io.micronaut.json.JsonMapper
import io.micronaut.serde.AbstractBasicSerdeCompileSpec

import java.time.LocalDateTime

class OracleJdbcJsonTextBasicSerdeCompileSpec extends AbstractBasicSerdeCompileSpec {

    @Override
    Class<JsonMapper> getJsonMapperClass() {
        return OracleJdbcJsonTextObjectMapper.class
    }

    // Maximum and minimum and enforced by database constraints
    LocalDateTime maxLocalDateTime() {
        return LocalDateTime.of(9999, 12, 31, 0, 0, 0)
    }

    LocalDateTime minLocalDateTime() {
        return LocalDateTime.of(1, 1, 1, 0, 0, 0)
    }

}
