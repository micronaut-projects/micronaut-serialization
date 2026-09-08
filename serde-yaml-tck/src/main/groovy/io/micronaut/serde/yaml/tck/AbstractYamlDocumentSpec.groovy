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
package io.micronaut.serde.yaml.tck

import io.micronaut.core.type.Argument
import spock.lang.Specification

/**
 * Validates how a YAML document as a whole is read: its root node, its markers and its comments.
 */
abstract class AbstractYamlDocumentSpec extends Specification implements YamlSpec {

    void "a root mapping is read"() {
        expect:
        readYaml("title: T\npages: 3\n", RecordBean) == new RecordBean("T", 3)
    }

    void "a root scalar is read"() {
        expect:
        readYaml("hello\n", String) == "hello"
        readYaml("42\n", Integer) == 42
        readYaml("true\n", Boolean)
    }

    void "explicit document markers are accepted"() {
        expect:
        readYaml("---\ntitle: T\npages: 3\n", RecordBean) == new RecordBean("T", 3)
        readYaml("---\ntitle: T\npages: 3\n...\n", RecordBean) == new RecordBean("T", 3)
    }

    void "comments are ignored"() {
        expect:
        readYaml('''# leading
title: T # inline
# between
pages: 3
# trailing
''', RecordBean) == new RecordBean("T", 3)
    }

    void "empty input is rejected"() {
        when:
        readYaml(yaml, RecordBean)

        then:
        thrown(Exception)

        where:
        yaml << ["", "# only a comment\n"]
    }

    void "a second document is rejected"() {
        when:
        readYaml("---\ntitle: A\npages: 1\n---\ntitle: B\npages: 2\n", RecordBean)

        then:
        thrown(Exception)
    }

    void "malformed yaml is rejected"() {
        when:
        readYaml(yaml, RecordBean)

        then:
        thrown(Exception)

        where:
        yaml << ["title: [unclosed\npages: 1\n", "title: A\n  pages: 1\n", "title:\n   - A\n  - B\n"]
    }

    void "a value of the wrong type is rejected"() {
        when:
        readYaml("title: T\npages: notANumber\n", RecordBean)

        then:
        thrown(Exception)
    }

    void "a document written by the mapper is read back by the mapper"() {
        given:
        def bean = new ObjectBean("outer", new SimpleBean("Hamza", 21))

        expect:
        readYaml(writeYaml(bean), ObjectBean).inner().name == "Hamza"
    }
}
