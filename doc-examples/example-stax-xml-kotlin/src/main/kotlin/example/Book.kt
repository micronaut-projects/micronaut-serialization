package example

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonRootName
import io.micronaut.serde.annotation.Serdeable
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty

@Serdeable // <1>
@JsonRootName("book") // <2>
class Book @JsonCreator constructor(
    @JacksonXmlProperty(isAttribute = true, localName = "isbn") // <3>
    @JsonProperty("isbn") val isbn: String,
    @JacksonXmlProperty(localName = "title") // <4>
    @JsonProperty("title") val title: String,
    @JacksonXmlElementWrapper(localName = "authors") // <5>
    @JacksonXmlProperty(localName = "author") // <6>
    @JsonProperty("authors") val authors: List<String>
)
