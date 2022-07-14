package io.micronaut.serde.oracle.jdbc.json

import io.micronaut.json.JsonMapper
import io.micronaut.serde.AbstractJsonCompileSpec

class OracleJdbcJsonTextCompileSpec extends AbstractJsonCompileSpec {

    @Override
    Class<JsonMapper> getJsonMapperClass() {
        return OracleJdbcJsonTextObjectMapper.class
    }
}
