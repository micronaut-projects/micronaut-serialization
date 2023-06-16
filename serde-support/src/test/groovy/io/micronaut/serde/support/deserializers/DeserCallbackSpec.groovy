package io.micronaut.serde.support.deserializers

import io.micronaut.context.annotation.Property
import io.micronaut.serde.ObjectMapper
import io.micronaut.serde.support.SimpleInfo
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@Property(name = "deser.callback", value = "true")
@MicronautTest
class DisableMicronautIntrospectionsSpec extends Specification {

    @Inject ObjectMapper jsonMapper
    @Inject DeserCallback deserCallback

    void "test deser callback"() {
        when:
            def value = jsonMapper.readValue("""{"info": "test"}""", SimpleInfo.class)

        then:
            value.info == "test"
            deserCallback.visited.size() == 1
            deserCallback.visited[0].beanType == SimpleInfo.class
    }

}
