package io.micronaut.serde.oracle.jdbc.json

import io.micronaut.json.JsonMapper
import io.micronaut.serde.AbstractBasicSerdeCompileSpec

class OracleJdbcJsonTextBasicSerdeCompileSpec extends AbstractBasicSerdeCompileSpec {

    @Override
    Class<JsonMapper> getJsonMapperClass() {
        return OracleJdbcJsonTextObjectMapper.class
    }

}
