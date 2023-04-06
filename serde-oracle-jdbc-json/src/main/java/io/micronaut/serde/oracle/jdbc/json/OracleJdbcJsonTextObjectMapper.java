/*
 * Copyright 2017-2021 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.serde.oracle.jdbc.json;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.core.annotation.Order;
import io.micronaut.json.JsonMapper;
import io.micronaut.serde.SerdeRegistry;
import jakarta.inject.Singleton;
import oracle.sql.json.OracleJsonGenerator;
import oracle.sql.json.OracleJsonParser;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * Implementation of the {@link JsonMapper} interface for Oracle JDBC Text JSON.
 *
 * @author Denis Stepanov
 * @since 1.2.0
 */
@Singleton
@BootstrapContextCompatible
@Order(199) // lower precedence than Jackson but higher than OracleJdbcJsonBinaryObjectMapper
public final class OracleJdbcJsonTextObjectMapper extends AbstractOracleJdbcJsonObjectMapper {

    public OracleJdbcJsonTextObjectMapper(SerdeRegistry registry) {
        super(registry);
    }

    public OracleJdbcJsonTextObjectMapper(SerdeRegistry registry, Class<?> view) {
        super(registry, view);
    }

    @Override
    public JsonMapper cloneWithViewClass(Class<?> viewClass) {
        return new OracleJdbcJsonTextObjectMapper(registry, viewClass);
    }

    @Override
    OracleJsonParser getJsonParser(InputStream inputStream) {
        return oracleJsonFactory.createJsonTextParser(inputStream);
    }

    @Override
    OracleJsonGenerator createJsonGenerator(OutputStream outputStream) {
        return oracleJsonFactory.createJsonTextGenerator(outputStream);
    }
}
