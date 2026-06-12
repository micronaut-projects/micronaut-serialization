/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.serde.jackson

class JsonKeySpec extends JsonCompileSpec {

    void "JsonKey method is used only for map keys"() {
        given:
            def context = buildContext('''
package example;

import com.fasterxml.jackson.annotation.JsonKey;
import com.fasterxml.jackson.annotation.JsonValue;
import io.micronaut.serde.annotation.Serdeable;
import java.util.Map;

@Serdeable
class Basket {
    private Map<Fruit, Integer> fruits;

    public Map<Fruit, Integer> getFruits() {
        return fruits;
    }

    public void setFruits(Map<Fruit, Integer> fruits) {
        this.fruits = fruits;
    }
}

@Serdeable
class Fruit {
    private final String name;
    private final String variety;

    Fruit(String name, String variety) {
        this.name = name;
        this.variety = variety;
    }

    @JsonKey
    public String getName() {
        return name;
    }

    @JsonValue
    public String getFullName() {
        return variety + " " + name;
    }
}
''')
            def fruit = newInstance(context, 'example.Fruit', 'Mango', 'Alphonso')
            def basket = newInstance(context, 'example.Basket')
            basket.fruits = new LinkedHashMap()
            basket.fruits.put(fruit, 1)

        expect:
            writeJson(jsonMapper, fruit) == '"Alphonso Mango"'
            writeJson(jsonMapper, basket) == '{"fruits":{"Mango":1}}'

        cleanup:
            context.close()
    }

    void "JsonKey field is used for map keys without changing value serialization"() {
        given:
            def context = buildContext('''
package example;

import com.fasterxml.jackson.annotation.JsonKey;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;
import java.util.Map;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Catalog {
    public Map<Product, Integer> products;
    public Product selected;
}

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Product {
    @JsonKey
    public String sku;
    public String name;

    Product(String sku, String name) {
        this.sku = sku;
        this.name = name;
    }
}
''')
            def product = newInstance(context, 'example.Product', 'SKU-1', 'Widget')
            def catalog = newInstance(context, 'example.Catalog')
            catalog.products = new LinkedHashMap()
            catalog.products.put(product, 2)
            catalog.selected = product

        expect:
            validateJsonWithoutOrder(
                jsonMapper,
                '{"products":{"SKU-1":2},"selected":{"sku":"SKU-1","name":"Widget"}}',
                writeJson(jsonMapper, catalog)
            )

        cleanup:
            context.close()
    }

    void "JsonKey field is used for runtime map key serialization"() {
        given:
            def context = buildContext(
                'example.RuntimeCatalog',
                '''
package example;

import com.fasterxml.jackson.annotation.JsonKey;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;
import java.util.LinkedHashMap;
import java.util.Map;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class RuntimeCatalog {
    public Map<RuntimeProduct, Integer> products = new LinkedHashMap<>();
    public RuntimeProduct selected;
}

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class RuntimeProduct {
    @JsonKey
    public String sku;
    public String name;

    RuntimeProduct(String sku, String name) {
        this.sku = sku;
        this.name = name;
    }
}
''',
                true,
                ['micronaut.serde.serialization.disable-generated-serializer': true]
            )
            def product = newInstance(context, 'example.RuntimeProduct', 'SKU-1', 'Widget')
            def catalog = newInstance(context, 'example.RuntimeCatalog')
            catalog.products.put(product, 2)
            catalog.selected = product

        expect:
            validateJsonWithoutOrder(
                jsonMapper,
                '{"products":{"SKU-1":2},"selected":{"sku":"SKU-1","name":"Widget"}}',
                writeJson(jsonMapper, catalog)
            )

        cleanup:
            context.close()
    }

    void "JsonKey false is ignored"() {
        given:
            def context = buildContext('''
package example;

import com.fasterxml.jackson.annotation.JsonKey;
import com.fasterxml.jackson.annotation.JsonValue;
import io.micronaut.serde.annotation.Serdeable;
import java.util.Map;

@Serdeable
class Basket {
    private Map<Fruit, Integer> fruits;

    public Map<Fruit, Integer> getFruits() {
        return fruits;
    }

    public void setFruits(Map<Fruit, Integer> fruits) {
        this.fruits = fruits;
    }
}

@Serdeable
class Fruit {
    private final String name;
    private final String variety;

    Fruit(String name, String variety) {
        this.name = name;
        this.variety = variety;
    }

    @JsonKey(false)
    public String getName() {
        return name;
    }

    @JsonValue
    public String getFullName() {
        return variety + " " + name;
    }
}
''')
            def fruit = newInstance(context, 'example.Fruit', 'Mango', 'Alphonso')
            def basket = newInstance(context, 'example.Basket')
            basket.fruits = new LinkedHashMap()
            basket.fruits.put(fruit, 1)

        expect:
            writeJson(jsonMapper, basket) == '{"fruits":{"Alphonso Mango":1}}'

        cleanup:
            context.close()
    }

    void "JsonKey method is used for enum map keys"() {
        given:
            def context = buildContext('''
package example;

import com.fasterxml.jackson.annotation.JsonKey;
import com.fasterxml.jackson.annotation.JsonValue;
import io.micronaut.serde.annotation.Serdeable;
import java.util.Map;

@Serdeable
class StatusHolder {
    private Map<Status, Integer> statuses;
    private Status status;

    public Map<Status, Integer> getStatuses() {
        return statuses;
    }

    public void setStatuses(Map<Status, Integer> statuses) {
        this.statuses = statuses;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }
}

@Serdeable
enum Status {
    OK("ok-key", "ok-value");

    private final String key;
    private final String value;

    Status(String key, String value) {
        this.key = key;
        this.value = value;
    }

    @JsonKey
    public String key() {
        return key;
    }

    @JsonValue
    public String value() {
        return value;
    }
}
''')
            def status = getEnum(context, 'example.Status.OK')
            def holder = newInstance(context, 'example.StatusHolder')
            holder.statuses = new LinkedHashMap()
            holder.statuses.put(status, 3)
            holder.status = status

        expect:
            validateJsonWithoutOrder(
                jsonMapper,
                '{"statuses":{"ok-key":3},"status":"ok-value"}',
                writeJson(jsonMapper, holder)
            )

        cleanup:
            context.close()
    }

    void "JsonKey composes with JsonValue for nested map key serialization"() {
        given:
            def context = buildContext('''
package example;

import com.fasterxml.jackson.annotation.JsonKey;
import com.fasterxml.jackson.annotation.JsonValue;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;
import java.util.LinkedHashMap;
import java.util.Map;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Catalog {
    public Map<CatalogEntry, String> entriesByKey = new LinkedHashMap<>();
    public Map<String, CatalogEntry> entriesByName = new LinkedHashMap<>();
    public Map<String, ValueOnlyCatalogEntry> valueOnlyEntries = new LinkedHashMap<>();
}

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class CatalogLabel {
    @JsonKey
    String key;

    @JsonValue
    String value;

    CatalogLabel(String key, String value) {
        this.key = key;
        this.value = value;
    }
}

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class CatalogEntry {
    @JsonKey
    @JsonValue
    CatalogLabel label;

    CatalogEntry(CatalogLabel label) {
        this.label = label;
    }
}

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class ValueOnlyCatalogEntry {
    @JsonValue
    CatalogLabel label;

    ValueOnlyCatalogEntry(CatalogLabel label) {
        this.label = label;
    }
}
''')
            def label = newInstance(context, 'example.CatalogLabel', 'display-key', 'display-value')
            def entry = newInstance(context, 'example.CatalogEntry', label)
            def valueOnlyEntry = newInstance(context, 'example.ValueOnlyCatalogEntry', label)
            def catalog = newInstance(context, 'example.Catalog')
            catalog.entriesByKey.put(entry, 'value')
            catalog.entriesByName.put('key', entry)
            catalog.valueOnlyEntries.put('key', valueOnlyEntry)

        expect:
            validateJsonWithoutOrder(
                jsonMapper,
                '{"entriesByKey":{"display-key":"value"},"entriesByName":{"key":"display-value"},"valueOnlyEntries":{"key":"display-value"}}',
                writeJson(jsonMapper, catalog)
            )

        cleanup:
            context.close()
    }
}
