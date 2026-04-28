package io.micronaut.serde.xml

import io.micronaut.core.type.Argument
//import io.micronaut.serde.AllTypesBean
//import io.micronaut.serde.ApiResponse
//import io.micronaut.serde.BeanWithExtraMethod
//import io.micronaut.serde.ConstructorArgs
//import io.micronaut.serde.Dummy
//import io.micronaut.serde.ObjectWithArray
//import io.micronaut.serde.ObjectWithArrayConstructor
//import io.micronaut.serde.ObjectWithArrayOfArray
//import io.micronaut.serde.ObjectWithArrayRecord
//import io.micronaut.serde.ObjectWithArrayRequired
//import io.micronaut.serde.RecordBean
//import io.micronaut.serde.Simple
import io.micronaut.serde.config.annotation.SerdeConfig
//import io.micronaut.serde.data.Users1
//import io.micronaut.serde.data.Users2
//import io.micronaut.serde.data.Users3
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import jakarta.inject.Named
import spock.lang.Ignore
import spock.lang.Specification

import java.nio.charset.StandardCharsets

@MicronautTest
class XmlBasicSerdeSpec extends Specification implements TestPropertyProvider, MicronautXmlSpec {


    @Inject
    @Named("xml")
    XmlObjectMapper xmlMapper

    @Override
    XmlObjectMapper getXmlMapper() {
        return xmlMapper
    }

    @Ignore("untill refactor decoder")
    def "missing list"() {
        given:
        def xml = "<ObjectWithArray />"
        when:
        def obj = xmlMapper.readValue(xml, Argument.of(ObjectWithArray))
        then:
        obj
        obj.vals == null
    }
    @Ignore("untill refactor decoder")
    def "missing list - constructor"() {
        given:
        def xml = "<ObjectWithArrayConstructor/>"
        when:
        def obj = xmlMapper.readValue(xml, Argument.of(ObjectWithArrayConstructor))
        then:
        obj
        obj.vals == null
    }
    @Ignore("untill refactor decoder")
    def "missing list - record"() {
        given:
        def xml = "<ObjectWithArrayRecord/>"
        when:
        def obj = xmlMapper.readValue(xml, Argument.of(ObjectWithArrayRecord))
        then:
        obj
        obj.vals() == null
    }
    @Ignore("untill refactor decoder")
    def "missing list - required"() {
        given:
        def xml = "<ObjectWithArrayRequired/>"
        when:
        xmlMapper.readValue(xml, Argument.of(ObjectWithArrayRequired))
        then:
        def e = thrown(Exception)
        e.message.contains("Required constructor parameter") || e.message.contains("Missing required creator property")
    }

//    void "test write simple"() {
//        when:
//        def bean = new Simple(name: "Test")
//        def result = writeXml(bean)
//        then:
//        //result == expectedXml("Simple", '{"name":"Test"}')
//        result == '<Simple><name>Test</name></Simple>'
//    }

    // ---- Nested ------------------------------------------------------------

    // still working on it
    // "The type parameter T in ApiResponse<T> is being resolved to LinkedHashMap instead of List<Dummy>"
    //@Ignore
//    void "test nested"() {
//        when:
//        def bean = new ApiResponse(List.of(new Dummy("Xyz")));
//        def argument = Argument.of(ApiResponse, Argument.listOf(Dummy))
//        def result = writeXml(argument, bean)
//        println result + "dqsdsqdsq"
//        then:
//        //result == expectedXml("ApiResponse", '{"content":[{"name":"Xyz"}]}')
//        result == '<ApiResponse><content><content><name>Xyz</name></content></content></ApiResponse>'
//
//        when:
//        def readBean = xmlMapper.readValue(result, argument)
//
//        then:
//        readBean.content.size() == 1
//        readBean.content[0].name == "Xyz"
//        readBean.content[1].name == "xcv"
//    }

    // ---- Constructor args

//    void "test read/write constructor args"() {
//        when:
//        def bean = new ConstructorArgs("test", 100)
//        bean.author = "Bob"
//        bean.other = "Something"
//        def result = writeXml(bean)
//        then:
//        result.contains("<title>test</title>")
//        result.contains("<author>Bob</author>")
//        result.contains("<pages>100</pages>")
//        result.contains("<other>Something</other>")
//        result.startsWith("<ConstructorArgs>")
//        result.trim().endsWith("</ConstructorArgs>")
//
//        when:
//        bean = xmlMapper.readValue(result, Argument.of(ConstructorArgs))
//        then:
//        bean.title == 'test'
//        bean.pages == 100
//        bean.other == 'Something'
//        bean.author == 'Bob'
//
//        when:
//        bean = xmlMapper.readValue(
//                //xmlBytes('{"other":"Something","author":"Bob","title":"test","pages":100}'),
//                "<ConstructorArgs><other>Something</other><author>Bob</author><title>test</title><pages>100</pages></ConstructorArgs>",
//                Argument.of(ConstructorArgs))
//        then:
//        bean.title == 'test'
//        bean.pages == 100
//        bean.other == 'Something'
//        bean.author == 'Bob'
//    }
    @Ignore("untill refactor decoder")
    def "validate arrays"() {
        given:
        def xml =
                "<ObjectWithArray>" +
                        "<vals>" +
                        "<vals>" +
                        "<val>A</val>" +
                        "</vals>" +
                        "<vals>" +
                        "<val>B</val>" +
                        "</vals>" +
                        "</vals>" +
                        "</ObjectWithArray>";
        when:
        def obj = xmlMapper.readValue(xml, Argument.of(ObjectWithArray))
        then:
        obj
        obj.vals.size() == 2
        obj.vals[0].val == "A"
        obj.vals[1].val == "B"
        objRepresentationMatches(obj, xml)
    }
    @Ignore("untill refactor decoder")
    def "validate empty arrays"() {
        given:
        def xml = '<ObjectWithArray></ObjectWithArray>'
        when:
        def obj = xmlMapper.readValue(xml, Argument.of(ObjectWithArray))
        then:
        obj
        obj.vals == null
    }
    @Ignore("untill refactor decoder")
    def "validate arrays with nulls"() {
        given:
        // TODO deal with null
        def xmls = "<ObjectWithArray>" +
                "<vals>" +
                "<vals>" +
                "<val>A</val>" +
                "</vals>" +
                "<vals>" +
                // missing "<val></val>" lead to null
                "</vals>" +
                "<vals>" +
                "<val>B</val>" +
                "</vals>" +
                "</vals>" +
                "</ObjectWithArray>"
        when:
        def obj = xmlMapper.readValue(xmls, Argument.of(ObjectWithArray))
        then:
        obj.vals.size() == 3
        obj.vals[0].val == "A"
        obj.vals[1] == null
        obj.vals[2].val == "B"
    }

    @Ignore("untill refactor decoder")
    def "validate arrays of arrays"() {
        //TODO raise exception, see the online formatter
        given:
        // nsi is off
        def xml = '<ObjectWithArrayOfArray><vals><SomeObject><SomeObject><val>A</val></SomeObject><SomeObject/><SomeObject><val>B</val></SomeObject></SomeObject></vals></ObjectWithArrayOfArray>'
        //def xml = "<ObjectWithArrayOfArray><vals><vals><vals><val>A</val></vals>" + "<vals xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:nil=\"true\"/>" + "<vals><val>B</val></vals></vals></vals></ObjectWithArrayOfArray>"
        when:
        def obj = xmlMapper.readValue(xml, Argument.of(ObjectWithArrayOfArray))
        println obj.toString()
        then:
        obj
        obj.vals.size() == 1
        obj.vals[0].size() == 3
        obj.vals[0][0].val == "A"
        obj.vals[0][1] == null
        obj.vals[0][2].val == "B"
        objRepresentationMatches(obj, xml)

    }

    @Ignore("untill refactor decoder")
    def "validate empty arrays of arrays"() {
        given:
        // def xml = expectedXml("ObjectWithArrayOfArray", '{"vals": [[]]}')
        def xml = "<ObjectWithArrayOfArray><vals><SomeObject><SomeObject/></SomeObject></vals></ObjectWithArrayOfArray>"

        when:

        def obj = xmlMapper.readValue(xml, Argument.of(ObjectWithArrayOfArray))
        println xml
        then:
        println "===>" + obj.toString()
        obj.vals.size() == 1
        obj.vals[0].size() == 1
        objRepresentationMatches(obj, xml)
    }
    @Ignore("untill refactor decoder")
    def "validate null arrays of arrays"() {
        given:
        // def xml = expectedXml("ObjectWithArrayOfArray", '{"vals": [null]}')
        def xml = '<ObjectWithArrayOfArray><vals><vals/></vals></ObjectWithArrayOfArray>'
        when:
        def obj = xmlMapper.readValue(xml, Argument.of(ObjectWithArrayOfArray))
        then:
        obj
        obj.vals.size() == 1
        obj.vals[0] == null
        objRepresentationMatches(obj, xml)
    }
    @Ignore("untill refactor decoder")
    def "validate arrays as null"() {
        given:
        //def xml = xmlBytes('{"ObjectWithArray":{"vals": null}}')
        def xml = "<ObjectWithArray><vals/></ObjectWithArray>"

        when:
        def obj = xmlMapper.readValue(xml, Argument.of(ObjectWithArray))
        println "==<" + obj.toString()
        then:
        obj
        obj.vals == null
        objRepresentationMatches(obj, xml)
    }
    @Ignore("untill refactor decoder")
    def "should deser all null types bean"() {
        given:
        def xml = "<AllTypesBean />"
        when:
        def obj = xmlMapper.readValue(
                xml,
                Argument.of(AllTypesBean))
        then:
        noExceptionThrown()
    }

//    def "all nullable fields null"() {
//        given:
//        def bean = new AllTypesBean()
//        // leave all nullable fields unset (null)
//
//        when:
//        def bytes = xmlMapper.writeValueAsBytes(bean)
//        def result = xmlMapper.readValue(bytes, Argument.of(AllTypesBean))
//
//        then:
//        noExceptionThrown()
//        !result.someBool
//        result.someInt   == 0
//        result.someLong  == 0L
//        result.someString  == null
//        result.someBoolean == null
//        result.bigDecimal  == null
//        result.bigInteger  == null
//
//    }

//    def "validate all types bean"() {
//        given:
//        def all = new AllTypesBean()
//        all.someBool = true
//        all.someInt = 123
//        all.someLong = 234
//        all.someByte = (byte) 34
//        all.someShort = (short) 567
//        all.someFloat = 11.22f
//        all.someDouble = 123.234D
//        all.someString = "Hello"
//        all.someBoolean = Boolean.TRUE
//        all.someInteger = 444
//        all.someLongObj = 555
//        all.someDoubleObj = 666.77d
//        all.someShortObj = 777
//        all.someFloatObj = 888.99f
//        all.someByteObj = 99
//        all.bigDecimal = BigDecimal.valueOf(12345.12345)
//        all.bigInteger = BigInteger.valueOf(123456789)
//        when:
//        def result = serializeDeserialize(all)
//        then:
//        result.someBool
//        result.someInt == 123
//        result.someLong == 234
//        result.someByte == (byte) 34
//        result.someShort == (short) 567
//        result.someFloat == 11.22f
//        result.someDouble == 123.234D
//        result.someString == "Hello"
//        result.someBoolean == Boolean.TRUE
//        result.someInteger == 444
//        result.someLongObj == 555
//        result.someDoubleObj == 666.77d
//        result.someShortObj == 777
//        result.someFloatObj == 888.99f
//        result.someByteObj == 99
//        result.bigDecimal == BigDecimal.valueOf(12345.12345)
//        result.bigInteger == BigInteger.valueOf(123456789)
//    }

    // Type-level round-trips (primitives as element text)

//    def "round-trip primitive types via AllTypesBean"() {
//        given:
//        def bean = new AllTypesBean()
//        bean.someInt    = intVal
//        bean.someLong   = longVal
//        bean.someDouble = doubleVal
//        bean.someBool   = boolVal
//
//        when:
//        def bytes  = xmlMapper.writeValueAsBytes(bean)
//        def result = xmlMapper.readValue(bytes, Argument.of(AllTypesBean))
//
//        then:
//        result.someInt    == intVal
//        result.someLong   == longVal
//        Math.abs(result.someDouble - doubleVal) < 0.001d
//        result.someBool   == boolVal
//
//        where:
//        intVal | longVal    | doubleVal | boolVal
//        0      | 0L         | 0.0d      | false
//        1      | 1L         | 1.1d      | true
//        -42    | -100000L   | -3.14d    | false
//        999    | 9999999999L| 123.456d  | true
//    }
//

    def "validate json node"() {
        //--
    }

    // ---- Skip unknown / decode null ----------------------------------------
    @Ignore("untill refactor decoder")
    def "should skip unknown values"() {
        when:
        def value = xmlMapper.readValue(
                '<AllTypesBean><unknown>ABC</unknown></AllTypesBean>',
                Argument.of(AllTypesBean))
        then:
        noExceptionThrown()
    }
    @Ignore("untill refactor decoder")
    def "should decode null"() {
        given:
        def xml = "<AllTypesBean>" +
                "<someBool/>" +
                "<someInt></someInt>" +
                "<bigDecimal></bigDecimal>" +
                "</AllTypesBean>"
        when:
        def value = xmlMapper.readValue(
                xml,
                Argument.of(AllTypesBean))
        then:
        value.someInt == 0
        !value.someBool
        value.bigDecimal == null
    }

    @Ignore("untill refactor decoder")
    def "should deser deep structure Users 1"() {
        given:
        def xml = "<Users1><users><users><_id>39771757156730064829</_id><index>1031703887</index><guid>ifhsrU6geU4PijjDE8Q5</guid><isActive>false</isActive><balance>TKl0GcwTs72S4CPx5rfg</balance><picture>FkKrg6ZOPC5REchlhixu5WgIl3gNAqq28iLtFm6dKfTSQs8d3P0cYxKsEvbvMB2C6BVgExop3khRlNSFE4SV8dVFitFs7RyyecN8</picture><age>5</age><eyeColor>AY79Pw4sYByUZEMLxnYJ</eyeColor><name>XjXrEZMuTvPnuOPBg7hL</name><gender>VaMcuWBHvnWvIlCC9q4T</gender><company>6pmCe1LxouRGfZD79ena</company><email>TboNtpmAS0ppZ07jITFE</email><phone>j8OoUhtmwBlI20EgD1LS</phone><address>Aqo4fSYBpvvAWTDqbFbK</address><about>1kXFSA2782BLqNBbKIbp</about><registered>Mc7h3gZJcQ11ShGQYdXI</registered><latitude>13.474549605725421</latitude><longitude>35.010833129741435</longitude><tags><tags>8tGfPhZkZD</tags><tags>XYmwuAAtZ4</tags><tags>u9iBDMpS9G</tags><tags>4udy1eRqme</tags><tags>Lg48Ogrf0I</tags><tags>zku019kVpo</tags><tags>iuIMkiZzog</tags><tags>MuI1uYeCjc</tags><tags>49n7qisFD8</tags><tags>TtVgWerCRh</tags><tags>H604QRJmi1</tags><tags>ZIQMfqInNH</tags><tags>CbDyjjA19F</tags><tags>pNFwPdkVdU</tags><tags>aPFLsUbIUh</tags><tags>fA735PT0Hd</tags><tags>00etYDYL87</tags><tags>mlyEf1lI2B</tags><tags>RQ05IJSzXF</tags><tags>3jJt0Zrkhw</tags><tags>ZINP8GH4Bm</tags><tags>XebX8UvviN</tags><tags>EXqZ9G0ATB</tags><tags>ssyzWZVAa2</tags></tags><friends><friends><id>2668</id><name>lcxeDXPbnoIxAPqTNdkwbcGIJxLnPe</name></friends><friends><id>9395</id><name>dxNBbezfkbotyCmFzjodONShlGFaAg</name></friends><friends><id>5249</id><name>fYHSDXScMSzQvxzFuuPHYWfyjdGQLg</name></friends><friends><id>4978</id><name>qfoxPWmoWUyUduVkRwhzyBusuflrFY</name></friends><friends><id>9710</id><name>vUAJwshFGLoBHfwLcsEVNLJLwdaCAg</name></friends><friends><id>7404</id><name>BhVMdvhPRdpwpDWAmfhNDikncdNgGr</name></friends><friends><id>1343</id><name>ZeDoizPcOBafZtVYDOmpzGoHekfoxf</name></friends><friends><id>7382</id><name>KtqXeVdCQJlwSNHkgkxuoIGdOWrmqG</name></friends><friends><id>1365</id><name>rCSTlgbmTAFhbSfPmnftcDLwdiKsHt</name></friends><friends><id>8037</id><name>PUvwVYoSvSTnwjJCQITTcwNvMOpxie</name></friends><friends><id>4858</id><name>cUfQfDIiyMfCMYBKGwhZSWnRRKwlxG</name></friends><friends><id>9141</id><name>rJxMGOWRjdkphthcaKTspFrMcvcLLb</name></friends><friends><id>9128</id><name>gcsYaolAQqrNMQTluIAKOkwYTWVUXe</name></friends><friends><id>2268</id><name>jwXOUcXAiLurRlgTdxyKWvsbNHfFxl</name></friends><friends><id>5447</id><name>whivfJXOdxoHtLIGpytTdbOXxlZpUY</name></friends><friends><id>7551</id><name>whykuIjZUgvOFGpmNHjoPeTeYCPNby</name></friends><friends><id>719</id><name>SmbiwQaORLdsbAlUZbQwgCKfuoPLVr</name></friends><friends><id>7773</id><name>LZmRMXmXXHzlzFFJAopDNnWkuBqndD</name></friends><friends><id>9602</id><name>xCNsDBFMygEwZuecJKTUrqeDLBJlrR</name></friends><friends><id>1536</id><name>hrfeFnKnmVgZDDOxAHgXfgcJSRyiXB</name></friends><friends><id>3549</id><name>NvvhXwWgCSaYijqhxsrxIWrHbBOOIa</name></friends></friends><greeting>hTAIJLspvLr8DJPG3jYh</greeting><favoriteFruit>f6ZsZ3saRGKMBCZLAkiP</favoriteFruit></users></users></Users1>"
        when:
        def obj = xmlMapper.readValue(xml, Argument.of(Users1))
        then:
        def result = writeXml(obj);
        XmlMatches(result, xml)
    }

    @Ignore("untill refactor decoder")
    def "should deser deep structure Users 2"() {
        given:
        def xml = "<Users2><users><users><_id>39771757156730064829</_id><index>1031703887</index><guid>ifhsrU6geU4PijjDE8Q5</guid><isActive>false</isActive><balance>TKl0GcwTs72S4CPx5rfg</balance><picture>FkKrg6ZOPC5REchlhixu5WgIl3gNAqq28iLtFm6dKfTSQs8d3P0cYxKsEvbvMB2C6BVgExop3khRlNSFE4SV8dVFitFs7RyyecN8</picture><age>5</age><eyeColor>AY79Pw4sYByUZEMLxnYJ</eyeColor><name>XjXrEZMuTvPnuOPBg7hL</name><gender>VaMcuWBHvnWvIlCC9q4T</gender><company>6pmCe1LxouRGfZD79ena</company><email>TboNtpmAS0ppZ07jITFE</email><phone>j8OoUhtmwBlI20EgD1LS</phone><address>Aqo4fSYBpvvAWTDqbFbK</address><about>1kXFSA2782BLqNBbKIbp</about><registered>Mc7h3gZJcQ11ShGQYdXI</registered><latitude>13.474549605725421</latitude><longitude>35.010833129741435</longitude><tags><tags>8tGfPhZkZD</tags><tags>XYmwuAAtZ4</tags><tags>u9iBDMpS9G</tags><tags>4udy1eRqme</tags><tags>Lg48Ogrf0I</tags><tags>zku019kVpo</tags><tags>iuIMkiZzog</tags><tags>MuI1uYeCjc</tags><tags>49n7qisFD8</tags><tags>TtVgWerCRh</tags><tags>H604QRJmi1</tags><tags>ZIQMfqInNH</tags><tags>CbDyjjA19F</tags><tags>pNFwPdkVdU</tags><tags>aPFLsUbIUh</tags><tags>fA735PT0Hd</tags><tags>00etYDYL87</tags><tags>mlyEf1lI2B</tags><tags>RQ05IJSzXF</tags><tags>3jJt0Zrkhw</tags><tags>ZINP8GH4Bm</tags><tags>XebX8UvviN</tags><tags>EXqZ9G0ATB</tags><tags>ssyzWZVAa2</tags></tags><friends><friends><id>2668</id><name>lcxeDXPbnoIxAPqTNdkwbcGIJxLnPe</name></friends><friends><id>9395</id><name>dxNBbezfkbotyCmFzjodONShlGFaAg</name></friends><friends><id>5249</id><name>fYHSDXScMSzQvxzFuuPHYWfyjdGQLg</name></friends><friends><id>4978</id><name>qfoxPWmoWUyUduVkRwhzyBusuflrFY</name></friends><friends><id>9710</id><name>vUAJwshFGLoBHfwLcsEVNLJLwdaCAg</name></friends><friends><id>7404</id><name>BhVMdvhPRdpwpDWAmfhNDikncdNgGr</name></friends><friends><id>1343</id><name>ZeDoizPcOBafZtVYDOmpzGoHekfoxf</name></friends><friends><id>7382</id><name>KtqXeVdCQJlwSNHkgkxuoIGdOWrmqG</name></friends><friends><id>1365</id><name>rCSTlgbmTAFhbSfPmnftcDLwdiKsHt</name></friends><friends><id>8037</id><name>PUvwVYoSvSTnwjJCQITTcwNvMOpxie</name></friends><friends><id>4858</id><name>cUfQfDIiyMfCMYBKGwhZSWnRRKwlxG</name></friends><friends><id>9141</id><name>rJxMGOWRjdkphthcaKTspFrMcvcLLb</name></friends><friends><id>9128</id><name>gcsYaolAQqrNMQTluIAKOkwYTWVUXe</name></friends><friends><id>2268</id><name>jwXOUcXAiLurRlgTdxyKWvsbNHfFxl</name></friends><friends><id>5447</id><name>whivfJXOdxoHtLIGpytTdbOXxlZpUY</name></friends><friends><id>7551</id><name>whykuIjZUgvOFGpmNHjoPeTeYCPNby</name></friends><friends><id>719</id><name>SmbiwQaORLdsbAlUZbQwgCKfuoPLVr</name></friends><friends><id>7773</id><name>LZmRMXmXXHzlzFFJAopDNnWkuBqndD</name></friends><friends><id>9602</id><name>xCNsDBFMygEwZuecJKTUrqeDLBJlrR</name></friends><friends><id>1536</id><name>hrfeFnKnmVgZDDOxAHgXfgcJSRyiXB</name></friends><friends><id>3549</id><name>NvvhXwWgCSaYijqhxsrxIWrHbBOOIa</name></friends></friends><greeting>hTAIJLspvLr8DJPG3jYh</greeting><favoriteFruit>f6ZsZ3saRGKMBCZLAkiP</favoriteFruit></users></users></Users2>"
        def xmlStripped = "<Users2><users><users><_id>39771757156730064829</_id><index>1031703887</index><guid>ifhsrU6geU4PijjDE8Q5</guid><isActive>false</isActive><balance>TKl0GcwTs72S4CPx5rfg</balance><picture>FkKrg6ZOPC5REchlhixu5WgIl3gNAqq28iLtFm6dKfTSQs8d3P0cYxKsEvbvMB2C6BVgExop3khRlNSFE4SV8dVFitFs7RyyecN8</picture><age>5</age><eyeColor>AY79Pw4sYByUZEMLxnYJ</eyeColor><name>XjXrEZMuTvPnuOPBg7hL</name><gender>VaMcuWBHvnWvIlCC9q4T</gender><company>6pmCe1LxouRGfZD79ena</company><email>TboNtpmAS0ppZ07jITFE</email><phone>j8OoUhtmwBlI20EgD1LS</phone><address>Aqo4fSYBpvvAWTDqbFbK</address><about>1kXFSA2782BLqNBbKIbp</about><registered>Mc7h3gZJcQ11ShGQYdXI</registered></users></users></Users2>";
        when:
        def obj = xmlMapper.readValue(xml, Users2.class)
        then:
        def result = writeXml(obj);
        XmlMatches(result, xmlStripped)
    }

    @Ignore("untill refactor decoder")
    def "should deser deep structure Users 3"() {
        given:
        def xml = "<Users3><users><users><_id>39771757156730064829</_id><index>1031703887</index><guid>ifhsrU6geU4PijjDE8Q5</guid><isActive>false</isActive><balance>TKl0GcwTs72S4CPx5rfg</balance><picture>FkKrg6ZOPC5REchlhixu5WgIl3gNAqq28iLtFm6dKfTSQs8d3P0cYxKsEvbvMB2C6BVgExop3khRlNSFE4SV8dVFitFs7RyyecN8</picture><age>5</age><eyeColor>AY79Pw4sYByUZEMLxnYJ</eyeColor><name>XjXrEZMuTvPnuOPBg7hL</name><gender>VaMcuWBHvnWvIlCC9q4T</gender><company>6pmCe1LxouRGfZD79ena</company><email>TboNtpmAS0ppZ07jITFE</email><phone>j8OoUhtmwBlI20EgD1LS</phone><address>Aqo4fSYBpvvAWTDqbFbK</address><about>1kXFSA2782BLqNBbKIbp</about><registered>Mc7h3gZJcQ11ShGQYdXI</registered><latitude>13.474549605725421</latitude><longitude>35.010833129741435</longitude><tags><tags>8tGfPhZkZD</tags><tags>XYmwuAAtZ4</tags><tags>u9iBDMpS9G</tags><tags>4udy1eRqme</tags><tags>Lg48Ogrf0I</tags><tags>zku019kVpo</tags><tags>iuIMkiZzog</tags><tags>MuI1uYeCjc</tags><tags>49n7qisFD8</tags><tags>TtVgWerCRh</tags><tags>H604QRJmi1</tags><tags>ZIQMfqInNH</tags><tags>CbDyjjA19F</tags><tags>pNFwPdkVdU</tags><tags>aPFLsUbIUh</tags><tags>fA735PT0Hd</tags><tags>00etYDYL87</tags><tags>mlyEf1lI2B</tags><tags>RQ05IJSzXF</tags><tags>3jJt0Zrkhw</tags><tags>ZINP8GH4Bm</tags><tags>XebX8UvviN</tags><tags>EXqZ9G0ATB</tags><tags>ssyzWZVAa2</tags></tags><friends><friends><id>2668</id><name>lcxeDXPbnoIxAPqTNdkwbcGIJxLnPe</name></friends><friends><id>9395</id><name>dxNBbezfkbotyCmFzjodONShlGFaAg</name></friends><friends><id>5249</id><name>fYHSDXScMSzQvxzFuuPHYWfyjdGQLg</name></friends><friends><id>4978</id><name>qfoxPWmoWUyUduVkRwhzyBusuflrFY</name></friends><friends><id>9710</id><name>vUAJwshFGLoBHfwLcsEVNLJLwdaCAg</name></friends><friends><id>7404</id><name>BhVMdvhPRdpwpDWAmfhNDikncdNgGr</name></friends><friends><id>1343</id><name>ZeDoizPcOBafZtVYDOmpzGoHekfoxf</name></friends><friends><id>7382</id><name>KtqXeVdCQJlwSNHkgkxuoIGdOWrmqG</name></friends><friends><id>1365</id><name>rCSTlgbmTAFhbSfPmnftcDLwdiKsHt</name></friends><friends><id>8037</id><name>PUvwVYoSvSTnwjJCQITTcwNvMOpxie</name></friends><friends><id>4858</id><name>cUfQfDIiyMfCMYBKGwhZSWnRRKwlxG</name></friends><friends><id>9141</id><name>rJxMGOWRjdkphthcaKTspFrMcvcLLb</name></friends><friends><id>9128</id><name>gcsYaolAQqrNMQTluIAKOkwYTWVUXe</name></friends><friends><id>2268</id><name>jwXOUcXAiLurRlgTdxyKWvsbNHfFxl</name></friends><friends><id>5447</id><name>whivfJXOdxoHtLIGpytTdbOXxlZpUY</name></friends><friends><id>7551</id><name>whykuIjZUgvOFGpmNHjoPeTeYCPNby</name></friends><friends><id>719</id><name>SmbiwQaORLdsbAlUZbQwgCKfuoPLVr</name></friends><friends><id>7773</id><name>LZmRMXmXXHzlzFFJAopDNnWkuBqndD</name></friends><friends><id>9602</id><name>xCNsDBFMygEwZuecJKTUrqeDLBJlrR</name></friends><friends><id>1536</id><name>hrfeFnKnmVgZDDOxAHgXfgcJSRyiXB</name></friends><friends><id>3549</id><name>NvvhXwWgCSaYijqhxsrxIWrHbBOOIa</name></friends></friends><greeting>hTAIJLspvLr8DJPG3jYh</greeting><favoriteFruit>f6ZsZ3saRGKMBCZLAkiP</favoriteFruit></users></users></Users3>"
        def xmlStripped = "<Users3><users><users><_id>39771757156730064829</_id><index>1031703887</index><guid>ifhsrU6geU4PijjDE8Q5</guid><isActive>false</isActive><balance>TKl0GcwTs72S4CPx5rfg</balance><picture>FkKrg6ZOPC5REchlhixu5WgIl3gNAqq28iLtFm6dKfTSQs8d3P0cYxKsEvbvMB2C6BVgExop3khRlNSFE4SV8dVFitFs7RyyecN8</picture><age>5</age><eyeColor>AY79Pw4sYByUZEMLxnYJ</eyeColor><name>XjXrEZMuTvPnuOPBg7hL</name><gender>VaMcuWBHvnWvIlCC9q4T</gender><company>6pmCe1LxouRGfZD79ena</company><email>TboNtpmAS0ppZ07jITFE</email><phone>j8OoUhtmwBlI20EgD1LS</phone><address>Aqo4fSYBpvvAWTDqbFbK</address><about>1kXFSA2782BLqNBbKIbp</about><registered>Mc7h3gZJcQ11ShGQYdXI</registered><latitude>13.474549605725421</latitude><longitude>35.010833129741435</longitude><tags><tags>8tGfPhZkZD</tags><tags>XYmwuAAtZ4</tags><tags>u9iBDMpS9G</tags><tags>4udy1eRqme</tags><tags>Lg48Ogrf0I</tags><tags>zku019kVpo</tags><tags>iuIMkiZzog</tags><tags>MuI1uYeCjc</tags><tags>49n7qisFD8</tags><tags>TtVgWerCRh</tags><tags>H604QRJmi1</tags><tags>ZIQMfqInNH</tags><tags>CbDyjjA19F</tags><tags>pNFwPdkVdU</tags><tags>aPFLsUbIUh</tags><tags>fA735PT0Hd</tags><tags>00etYDYL87</tags><tags>mlyEf1lI2B</tags><tags>RQ05IJSzXF</tags><tags>3jJt0Zrkhw</tags><tags>ZINP8GH4Bm</tags><tags>XebX8UvviN</tags><tags>EXqZ9G0ATB</tags><tags>ssyzWZVAa2</tags></tags><friends><friends><id>2668</id><name>lcxeDXPbnoIxAPqTNdkwbcGIJxLnPe</name></friends><friends><id>9395</id><name>dxNBbezfkbotyCmFzjodONShlGFaAg</name></friends><friends><id>5249</id><name>fYHSDXScMSzQvxzFuuPHYWfyjdGQLg</name></friends><friends><id>4978</id><name>qfoxPWmoWUyUduVkRwhzyBusuflrFY</name></friends><friends><id>9710</id><name>vUAJwshFGLoBHfwLcsEVNLJLwdaCAg</name></friends><friends><id>7404</id><name>BhVMdvhPRdpwpDWAmfhNDikncdNgGr</name></friends><friends><id>1343</id><name>ZeDoizPcOBafZtVYDOmpzGoHekfoxf</name></friends><friends><id>7382</id><name>KtqXeVdCQJlwSNHkgkxuoIGdOWrmqG</name></friends><friends><id>1365</id><name>rCSTlgbmTAFhbSfPmnftcDLwdiKsHt</name></friends><friends><id>8037</id><name>PUvwVYoSvSTnwjJCQITTcwNvMOpxie</name></friends><friends><id>4858</id><name>cUfQfDIiyMfCMYBKGwhZSWnRRKwlxG</name></friends><friends><id>9141</id><name>rJxMGOWRjdkphthcaKTspFrMcvcLLb</name></friends><friends><id>9128</id><name>gcsYaolAQqrNMQTluIAKOkwYTWVUXe</name></friends><friends><id>2268</id><name>jwXOUcXAiLurRlgTdxyKWvsbNHfFxl</name></friends><friends><id>5447</id><name>whivfJXOdxoHtLIGpytTdbOXxlZpUY</name></friends><friends><id>7551</id><name>whykuIjZUgvOFGpmNHjoPeTeYCPNby</name></friends><friends><id>719</id><name>SmbiwQaORLdsbAlUZbQwgCKfuoPLVr</name></friends><friends><id>7773</id><name>LZmRMXmXXHzlzFFJAopDNnWkuBqndD</name></friends><friends><id>9602</id><name>xCNsDBFMygEwZuecJKTUrqeDLBJlrR</name></friends><friends><id>1536</id><name>hrfeFnKnmVgZDDOxAHgXfgcJSRyiXB</name></friends><friends><id>3549</id><name>NvvhXwWgCSaYijqhxsrxIWrHbBOOIa</name></friends></friends></users></users></Users3>";
        when:
        def obj = xmlMapper.readValue(xml, Users3.class)
        then:
        def result = writeXml(obj);
        XmlMatches(result, xmlStripped)
    }
//
//    void "test read/write record"() {
//        when:
//        def bean = new RecordBean("fizz", "buzz")
//        def result = writeXml(bean)
//        then:
//        //result == expectedXml("RecordBean", '{"foo":"fizz","bar":"buzz"}')
//        result == '<RecordBean><foo>fizz</foo><bar>buzz</bar></RecordBean>'
//
//        when:
//        bean = xmlMapper.readValue(result, Argument.of(RecordBean))
//        then:
//        bean.foo() == 'fizz'
//        bean.bar() == 'buzz'
//
//    }
//
//    void "test a bean with an extra executable method"() {
//        when:
//        def bean = new BeanWithExtraMethod()
//        bean.name = "Bob"
//        def result = writeXml(bean)
//        then:
//        result == '<BeanWithExtraMethod><name>Bob</name></BeanWithExtraMethod>'
//
//        when:
//        bean = xmlMapper.readValue(result, Argument.of(BeanWithExtraMethod))
//        then:
//        bean.name == 'Bob'
//    }
    @Ignore("untill refactor decoder")
    def "round-trip via decodeArbitrary - Object-typed field preserves value"() {
        given:
        def xml = '<ArbitraryBean><name>hello</name><value>world</value></ArbitraryBean>'
        when:
        def obj = xmlMapper.readValue(xml, Argument.of(ArbitraryBean))
        then:
        obj.name  == 'hello'
        obj.value == 'world'
        objRepresentationMatches(obj, xml)
    }

    @Ignore("untill refactor decoder")
    def "NestedOnlyBean - Object field decoded as Map"() {
        given:
        def xml = '<NestedOnlyBean><nested><key>hello</key></nested></NestedOnlyBean>'
        when:
        def obj = xmlMapper.readValue(xml, Argument.of(NestedOnlyBean))
        then:
        obj.nested instanceof Map
        (obj.nested as Map).get('key') == 'hello'
    }
    @Ignore("untill refactor decoder")
    def "ItemsOnlyBean - List<Object> is the sole field"() {
        given:
        def xml = '<ItemsOnlyBean><items><items>alpha</items><items>beta</items></items></ItemsOnlyBean>'
        when:
        def obj = xmlMapper.readValue(xml, Argument.of(ItemsOnlyBean))
        then:
        obj.items instanceof List
        obj.items == ['alpha', 'beta']
    }

    @Override
    Map<String, String> getProperties() {
        [
                "micronaut.serde.serialization.inclusion": SerdeConfig.SerInclude.ALWAYS.name(),
                "micronaut.serde.xml.xml-write-features.WRITE_NULLS_AS_XSI_NIL":"false"
        ]
    }
}
