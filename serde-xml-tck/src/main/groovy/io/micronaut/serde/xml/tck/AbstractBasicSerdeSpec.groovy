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
package io.micronaut.serde.xml.tck

import io.micronaut.core.type.Argument
import io.micronaut.test.support.TestPropertyProvider
import spock.lang.Ignore
import spock.lang.Specification

abstract class AbstractBasicSerdeSpec extends Specification implements TestPropertyProvider, XmlSpec {

    def "Simple Bean"(){
        given:
            def bean = new SimpleBean(21, "Hamza")
            def expectedXml = "<SimpleBean>" +
                                    "<age>21</age>" +
                                    "<name>Hamza</name>" +
                                "</SimpleBean>";
        when:
            def xml = writeXml(bean)
        then:
            xml == expectedXml

    }

    def "Outer and Inner Bean"(){
        given:
            def inner = new SimpleBean(21, "Hamza");
            def bean = new ObjectBean(inner);
        when:
            def xml = writeXml(bean)
        then:
            xml == "<ObjectBean><simpleBeans><age>21</age><name>Hamza</name></simpleBeans></ObjectBean>"

    }

    def "Custom Bean"() {
        given:
            def bean = new CustomBean("A1", "A2", List.of("B1", "B2"));
        when:
            def xml = writeXml(bean)
        then:
            xml == "<CustomBean><A1>A1</A1><B1>A2</B1><C1><C1>B1</C1><C1>B2</C1></C1></CustomBean>"
    }

    def "Nested List"(){
        given:
            def bean = new SimpleBean(21, "Hamza");
            def nestedList = new NestedList(List.of(bean));
        when:
            def xml = writeXml(nestedList)
        then:
            xml == "<NestedList><nestedLists><nestedLists><age>21</age><name>Hamza</name></nestedLists></nestedLists></NestedList>"
    }

    def "missing list"() {
        given:
            def xml = "<ObjectWithArray/>"
        when:
            def obj = readXml(xml, Argument.of(ObjectWithArray))
        then:
            obj
            obj.vals == null
    }

    def "missing list - constructor"() {
        given:
            def xml = "<ObjectWithArrayConstructor/>"
        when:
            def obj = readXml(xml, Argument.of(ObjectWithArrayConstructor))
        then:
            obj
            obj.vals == null
    }

    def "missing list - record"() {
        given:
            def xml = "<ObjectWithArrayRecord/>"
        when:
            def obj = readXml(xml, Argument.of(ObjectWithArrayRecord))
        then:
            obj
            obj.vals() == null
    }

    def "missing list - required"() {
        given:
            def xml = "<ObjectWithArrayRequired/>"
        when:
            readXml(xml, Argument.of(ObjectWithArrayRequired))
        then:
            def e = thrown(Exception)
            e.message.contains("Required constructor parameter") || e.message.contains("Missing required creator property")
    }

    def "validate arrays"() {
        given:
            def xml = "<ObjectWithArray><vals><vals><val>A</val></vals><vals><val>B</val></vals></vals></ObjectWithArray>"
        when:
            def obj = readXml(xml, Argument.of(ObjectWithArray))
        then:
            obj
            obj.vals.size() == 2
            obj.vals[0].val == "A"
            obj.vals[1].val == "B"
            objRepresentationMatches(obj, xml)
    }

    def "validate empty arrays"() {
        given:
            def xml = '<ObjectWithArray></ObjectWithArray>'
        when:
            def obj = readXml(xml, Argument.of(ObjectWithArray))
        then:
            obj
            obj.vals == null
    }

    def "validate arrays with nulls"() {
        given:
            def xml = "<ObjectWithArray><vals><vals><val>A</val></vals><vals/><vals><val>B</val></vals></vals></ObjectWithArray>"
        when:
            def obj = readXml(xml, Argument.of(ObjectWithArray))
        then:
            obj
            obj.vals.size() == 3
            obj.vals[0].val == "A"
            obj.vals[1].val == null
            obj.vals[2].val == "B"
    }

    def "missing nested list"() {
        given:
            def xml = "<ObjectWithArrayOfArray/>"
        when:
            def obj = readXml(xml, Argument.of(ObjectWithArrayOfArray))
        then:
            obj
            obj.vals == null
    }

    def "validate arrays of arrays"() {
        given:
            def xml = '<ObjectWithArrayOfArray><vals><SomeObject><SomeObject><val>A</val></SomeObject><SomeObject/><SomeObject><val>B</val></SomeObject></SomeObject></vals></ObjectWithArrayOfArray>'
        when:
            def obj = readXml(xml, Argument.of(ObjectWithArrayOfArray))
        then:
            obj
            obj.vals.size() == 1
            obj.vals[0].size() == 3
            obj.vals[0][0].val == "A"
            obj.vals[0][1].val == null
            obj.vals[0][2].val == "B"
    }

    def "validate empty arrays of arrays"() {
        given:
            def xml = "<ObjectWithArrayOfArray><vals><SomeObject><SomeObject/></SomeObject></vals></ObjectWithArrayOfArray>"
        when:
            def obj = readXml(xml, Argument.of(ObjectWithArrayOfArray))
        then:
            obj
            obj.vals.size() == 1
            obj.vals[0].size() == 1
    }

    def "should deser all null types bean"() {
        given:
            def xml = "<AllTypesBean/>"
        when:
            readXml(xml, Argument.of(AllTypesBean))
        then:
            noExceptionThrown()
    }

    def "all nullable fields null"() {
        given:
            def bean = new AllTypesBean()
        when:
            def result = readXml(writeXmlAsBytes(bean), Argument.of(AllTypesBean))
        then:
            noExceptionThrown()
            !result.someBool
            result.someInt == 0
            result.someLong == 0L
            result.someString == null
            result.someBoolean == null
            result.bigDecimal == null
            result.bigInteger == null
    }

    def "validate all types bean"() {
        given:
            def all = new AllTypesBean()
            all.someBool = true
            all.someInt = 123
            all.someLong = 234
            all.someByte = (byte) 34
            all.someShort = (short) 567
            all.someFloat = 11.22f
            all.someDouble = 123.234D
            all.someString = "Hello"
            all.someBoolean = Boolean.TRUE
            all.someInteger = 444
            all.someLongObj = 555
            all.someDoubleObj = 666.77d
            all.someShortObj = 777
            all.someFloatObj = 888.99f
            all.someByteObj = 99
            all.bigDecimal = BigDecimal.valueOf(12345.12345)
            all.bigInteger = BigInteger.valueOf(123456789)
        when:
            def result = serializeDeserialize(all)
        then:
            result.someBool
            result.someInt == 123
            result.someLong == 234
            result.someByte == (byte) 34
            result.someShort == (short) 567
            result.someFloat == 11.22f
            result.someDouble == 123.234D
            result.someString == "Hello"
            result.someBoolean == Boolean.TRUE
            result.someInteger == 444
            result.someLongObj == 555
            result.someDoubleObj == 666.77d
            result.someShortObj == 777
            result.someFloatObj == 888.99f
            result.someByteObj == 99
            result.bigDecimal == BigDecimal.valueOf(12345.12345)
            result.bigInteger == BigInteger.valueOf(123456789)
    }

    def "round-trip primitive types via AllTypesBean"() {
        given:
            def bean = new AllTypesBean()
            bean.someInt = intVal
            bean.someLong = longVal
            bean.someDouble = doubleVal
            bean.someBool = boolVal
        when:
            def result = readXml(writeXmlAsBytes(bean), Argument.of(AllTypesBean))
        then:
            result.someInt == intVal
            result.someLong == longVal
            Math.abs(result.someDouble - doubleVal) < 0.001d
            result.someBool == boolVal
        where:
            intVal | longVal     | doubleVal | boolVal
            0      | 0L          | 0.0d      | false
            1      | 1L          | 1.1d      | true
            -42    | -100000L    | -3.14d    | false
            999    | 9999999999L | 123.456d  | true
    }

    def "should skip unknown values"() {
        when:
            readXml('<AllTypesBean><unknown>ABC</unknown></AllTypesBean>', Argument.of(AllTypesBean))
        then:
            noExceptionThrown()
    }

    def "shouldn't decode null on values"() {
        given:
            def xml = "<AllTypesBean><someBool/><someInt></someInt><bigDecimal></bigDecimal></AllTypesBean>"

        when:
            readXml(xml, Argument.of(AllTypesBean))
        then:
            thrown(Exception)

    }

    def "should deser deep structure Users 1"() {
        given:
            def xml = "<Users1><users><users><_id>39771757156730064829</_id><index>1031703887</index><guid>ifhsrU6geU4PijjDE8Q5</guid><isActive>false</isActive><balance>TKl0GcwTs72S4CPx5rfg</balance><picture>FkKrg6ZOPC5REchlhixu5WgIl3gNAqq28iLtFm6dKfTSQs8d3P0cYxKsEvbvMB2C6BVgExop3khRlNSFE4SV8dVFitFs7RyyecN8</picture><age>5</age><eyeColor>AY79Pw4sYByUZEMLxnYJ</eyeColor><name>XjXrEZMuTvPnuOPBg7hL</name><gender>VaMcuWBHvnWvIlCC9q4T</gender><company>6pmCe1LxouRGfZD79ena</company><email>TboNtpmAS0ppZ07jITFE</email><phone>j8OoUhtmwBlI20EgD1LS</phone><address>Aqo4fSYBpvvAWTDqbFbK</address><about>1kXFSA2782BLqNBbKIbp</about><registered>Mc7h3gZJcQ11ShGQYdXI</registered><latitude>13.474549605725421</latitude><longitude>35.010833129741435</longitude><tags><tags>8tGfPhZkZD</tags><tags>XYmwuAAtZ4</tags><tags>u9iBDMpS9G</tags><tags>4udy1eRqme</tags><tags>Lg48Ogrf0I</tags><tags>zku019kVpo</tags><tags>iuIMkiZzog</tags><tags>MuI1uYeCjc</tags><tags>49n7qisFD8</tags><tags>TtVgWerCRh</tags><tags>H604QRJmi1</tags><tags>ZIQMfqInNH</tags><tags>CbDyjjA19F</tags><tags>pNFwPdkVdU</tags><tags>aPFLsUbIUh</tags><tags>fA735PT0Hd</tags><tags>00etYDYL87</tags><tags>mlyEf1lI2B</tags><tags>RQ05IJSzXF</tags><tags>3jJt0Zrkhw</tags><tags>ZINP8GH4Bm</tags><tags>XebX8UvviN</tags><tags>EXqZ9G0ATB</tags><tags>ssyzWZVAa2</tags></tags><friends><friends><id>2668</id><name>lcxeDXPbnoIxAPqTNdkwbcGIJxLnPe</name></friends><friends><id>9395</id><name>dxNBbezfkbotyCmFzjodONShlGFaAg</name></friends><friends><id>5249</id><name>fYHSDXScMSzQvxzFuuPHYWfyjdGQLg</name></friends><friends><id>4978</id><name>qfoxPWmoWUyUduVkRwhzyBusuflrFY</name></friends><friends><id>9710</id><name>vUAJwshFGLoBHfwLcsEVNLJLwdaCAg</name></friends><friends><id>7404</id><name>BhVMdvhPRdpwpDWAmfhNDikncdNgGr</name></friends><friends><id>1343</id><name>ZeDoizPcOBafZtVYDOmpzGoHekfoxf</name></friends><friends><id>7382</id><name>KtqXeVdCQJlwSNHkgkxuoIGdOWrmqG</name></friends><friends><id>1365</id><name>rCSTlgbmTAFhbSfPmnftcDLwdiKsHt</name></friends><friends><id>8037</id><name>PUvwVYoSvSTnwjJCQITTcwNvMOpxie</name></friends><friends><id>4858</id><name>cUfQfDIiyMfCMYBKGwhZSWnRRKwlxG</name></friends><friends><id>9141</id><name>rJxMGOWRjdkphthcaKTspFrMcvcLLb</name></friends><friends><id>9128</id><name>gcsYaolAQqrNMQTluIAKOkwYTWVUXe</name></friends><friends><id>2268</id><name>jwXOUcXAiLurRlgTdxyKWvsbNHfFxl</name></friends><friends><id>5447</id><name>whivfJXOdxoHtLIGpytTdbOXxlZpUY</name></friends><friends><id>7551</id><name>whykuIjZUgvOFGpmNHjoPeTeYCPNby</name></friends><friends><id>719</id><name>SmbiwQaORLdsbAlUZbQwgCKfuoPLVr</name></friends><friends><id>7773</id><name>LZmRMXmXXHzlzFFJAopDNnWkuBqndD</name></friends><friends><id>9602</id><name>xCNsDBFMygEwZuecJKTUrqeDLBJlrR</name></friends><friends><id>1536</id><name>hrfeFnKnmVgZDDOxAHgXfgcJSRyiXB</name></friends><friends><id>3549</id><name>NvvhXwWgCSaYijqhxsrxIWrHbBOOIa</name></friends></friends><greeting>hTAIJLspvLr8DJPG3jYh</greeting><favoriteFruit>f6ZsZ3saRGKMBCZLAkiP</favoriteFruit></users></users></Users1>"
        when:
            def obj = readXml(xml, Argument.of(Users1))
        then:
            obj.users.size() == 1
            obj.users[0]._id == "39771757156730064829"
            obj.users[0].tags.size() == 24
            obj.users[0].friends.size() == 21
            obj.users[0].favoriteFruit == "f6ZsZ3saRGKMBCZLAkiP"
            writeXml(obj).contains("<favoriteFruit>f6ZsZ3saRGKMBCZLAkiP</favoriteFruit>")
    }

    def "should deser deep structure Users 2"() {
        given:
            def xml = "<Users2><users><users><_id>39771757156730064829</_id><index>1031703887</index><guid>ifhsrU6geU4PijjDE8Q5</guid><isActive>false</isActive><balance>TKl0GcwTs72S4CPx5rfg</balance><picture>FkKrg6ZOPC5REchlhixu5WgIl3gNAqq28iLtFm6dKfTSQs8d3P0cYxKsEvbvMB2C6BVgExop3khRlNSFE4SV8dVFitFs7RyyecN8</picture><age>5</age><eyeColor>AY79Pw4sYByUZEMLxnYJ</eyeColor><name>XjXrEZMuTvPnuOPBg7hL</name><gender>VaMcuWBHvnWvIlCC9q4T</gender><company>6pmCe1LxouRGfZD79ena</company><email>TboNtpmAS0ppZ07jITFE</email><phone>j8OoUhtmwBlI20EgD1LS</phone><address>Aqo4fSYBpvvAWTDqbFbK</address><about>1kXFSA2782BLqNBbKIbp</about><registered>Mc7h3gZJcQ11ShGQYdXI</registered><latitude>13.474549605725421</latitude><longitude>35.010833129741435</longitude><tags><tags>8tGfPhZkZD</tags><tags>XYmwuAAtZ4</tags><tags>u9iBDMpS9G</tags><tags>4udy1eRqme</tags><tags>Lg48Ogrf0I</tags><tags>zku019kVpo</tags><tags>iuIMkiZzog</tags><tags>MuI1uYeCjc</tags><tags>49n7qisFD8</tags><tags>TtVgWerCRh</tags><tags>H604QRJmi1</tags><tags>ZIQMfqInNH</tags><tags>CbDyjjA19F</tags><tags>pNFwPdkVdU</tags><tags>aPFLsUbIUh</tags><tags>fA735PT0Hd</tags><tags>00etYDYL87</tags><tags>mlyEf1lI2B</tags><tags>RQ05IJSzXF</tags><tags>3jJt0Zrkhw</tags><tags>ZINP8GH4Bm</tags><tags>XebX8UvviN</tags><tags>EXqZ9G0ATB</tags><tags>ssyzWZVAa2</tags></tags><friends><friends><id>2668</id><name>lcxeDXPbnoIxAPqTNdkwbcGIJxLnPe</name></friends><friends><id>9395</id><name>dxNBbezfkbotyCmFzjodONShlGFaAg</name></friends><friends><id>5249</id><name>fYHSDXScMSzQvxzFuuPHYWfyjdGQLg</name></friends><friends><id>4978</id><name>qfoxPWmoWUyUduVkRwhzyBusuflrFY</name></friends><friends><id>9710</id><name>vUAJwshFGLoBHfwLcsEVNLJLwdaCAg</name></friends><friends><id>7404</id><name>BhVMdvhPRdpwpDWAmfhNDikncdNgGr</name></friends><friends><id>1343</id><name>ZeDoizPcOBafZtVYDOmpzGoHekfoxf</name></friends><friends><id>7382</id><name>KtqXeVdCQJlwSNHkgkxuoIGdOWrmqG</name></friends><friends><id>1365</id><name>rCSTlgbmTAFhbSfPmnftcDLwdiKsHt</name></friends><friends><id>8037</id><name>PUvwVYoSvSTnwjJCQITTcwNvMOpxie</name></friends><friends><id>4858</id><name>cUfQfDIiyMfCMYBKGwhZSWnRRKwlxG</name></friends><friends><id>9141</id><name>rJxMGOWRjdkphthcaKTspFrMcvcLLb</name></friends><friends><id>9128</id><name>gcsYaolAQqrNMQTluIAKOkwYTWVUXe</name></friends><friends><id>2268</id><name>jwXOUcXAiLurRlgTdxyKWvsbNHfFxl</name></friends><friends><id>5447</id><name>whivfJXOdxoHtLIGpytTdbOXxlZpUY</name></friends><friends><id>7551</id><name>whykuIjZUgvOFGpmNHjoPeTeYCPNby</name></friends><friends><id>719</id><name>SmbiwQaORLdsbAlUZbQwgCKfuoPLVr</name></friends><friends><id>7773</id><name>LZmRMXmXXHzlzFFJAopDNnWkuBqndD</name></friends><friends><id>9602</id><name>xCNsDBFMygEwZuecJKTUrqeDLBJlrR</name></friends><friends><id>1536</id><name>hrfeFnKnmVgZDDOxAHgXfgcJSRyiXB</name></friends><friends><id>3549</id><name>NvvhXwWgCSaYijqhxsrxIWrHbBOOIa</name></friends></friends><greeting>hTAIJLspvLr8DJPG3jYh</greeting><favoriteFruit>f6ZsZ3saRGKMBCZLAkiP</favoriteFruit></users></users></Users2>"
        when:
            def obj = readXml(xml, Argument.of(Users2))
        then:
            obj.users.size() == 1
            obj.users[0]._id == "39771757156730064829"
            obj.users[0].registered == "Mc7h3gZJcQ11ShGQYdXI"
            !writeXml(obj).contains("<tags>")
            !writeXml(obj).contains("<friends>")
    }

    def "should deser deep structure Users 3"() {
        given:
            def xml = "<Users3><users><users><_id>39771757156730064829</_id><index>1031703887</index><guid>ifhsrU6geU4PijjDE8Q5</guid><isActive>false</isActive><balance>TKl0GcwTs72S4CPx5rfg</balance><picture>FkKrg6ZOPC5REchlhixu5WgIl3gNAqq28iLtFm6dKfTSQs8d3P0cYxKsEvbvMB2C6BVgExop3khRlNSFE4SV8dVFitFs7RyyecN8</picture><age>5</age><eyeColor>AY79Pw4sYByUZEMLxnYJ</eyeColor><name>XjXrEZMuTvPnuOPBg7hL</name><gender>VaMcuWBHvnWvIlCC9q4T</gender><company>6pmCe1LxouRGfZD79ena</company><email>TboNtpmAS0ppZ07jITFE</email><phone>j8OoUhtmwBlI20EgD1LS</phone><address>Aqo4fSYBpvvAWTDqbFbK</address><about>1kXFSA2782BLqNBbKIbp</about><registered>Mc7h3gZJcQ11ShGQYdXI</registered><latitude>13.474549605725421</latitude><longitude>35.010833129741435</longitude><tags><tags>8tGfPhZkZD</tags><tags>XYmwuAAtZ4</tags><tags>u9iBDMpS9G</tags><tags>4udy1eRqme</tags><tags>Lg48Ogrf0I</tags><tags>zku019kVpo</tags><tags>iuIMkiZzog</tags><tags>MuI1uYeCjc</tags><tags>49n7qisFD8</tags><tags>TtVgWerCRh</tags><tags>H604QRJmi1</tags><tags>ZIQMfqInNH</tags><tags>CbDyjjA19F</tags><tags>pNFwPdkVdU</tags><tags>aPFLsUbIUh</tags><tags>fA735PT0Hd</tags><tags>00etYDYL87</tags><tags>mlyEf1lI2B</tags><tags>RQ05IJSzXF</tags><tags>3jJt0Zrkhw</tags><tags>ZINP8GH4Bm</tags><tags>XebX8UvviN</tags><tags>EXqZ9G0ATB</tags><tags>ssyzWZVAa2</tags></tags><friends><friends><id>2668</id><name>lcxeDXPbnoIxAPqTNdkwbcGIJxLnPe</name></friends><friends><id>9395</id><name>dxNBbezfkbotyCmFzjodONShlGFaAg</name></friends><friends><id>5249</id><name>fYHSDXScMSzQvxzFuuPHYWfyjdGQLg</name></friends><friends><id>4978</id><name>qfoxPWmoWUyUduVkRwhzyBusuflrFY</name></friends><friends><id>9710</id><name>vUAJwshFGLoBHfwLcsEVNLJLwdaCAg</name></friends><friends><id>7404</id><name>BhVMdvhPRdpwpDWAmfhNDikncdNgGr</name></friends><friends><id>1343</id><name>ZeDoizPcOBafZtVYDOmpzGoHekfoxf</name></friends><friends><id>7382</id><name>KtqXeVdCQJlwSNHkgkxuoIGdOWrmqG</name></friends><friends><id>1365</id><name>rCSTlgbmTAFhbSfPmnftcDLwdiKsHt</name></friends><friends><id>8037</id><name>PUvwVYoSvSTnwjJCQITTcwNvMOpxie</name></friends><friends><id>4858</id><name>cUfQfDIiyMfCMYBKGwhZSWnRRKwlxG</name></friends><friends><id>9141</id><name>rJxMGOWRjdkphthcaKTspFrMcvcLLb</name></friends><friends><id>9128</id><name>gcsYaolAQqrNMQTluIAKOkwYTWVUXe</name></friends><friends><id>2268</id><name>jwXOUcXAiLurRlgTdxyKWvsbNHfFxl</name></friends><friends><id>5447</id><name>whivfJXOdxoHtLIGpytTdbOXxlZpUY</name></friends><friends><id>7551</id><name>whykuIjZUgvOFGpmNHjoPeTeYCPNby</name></friends><friends><id>719</id><name>SmbiwQaORLdsbAlUZbQwgCKfuoPLVr</name></friends><friends><id>7773</id><name>LZmRMXmXXHzlzFFJAopDNnWkuBqndD</name></friends><friends><id>9602</id><name>xCNsDBFMygEwZuecJKTUrqeDLBJlrR</name></friends><friends><id>1536</id><name>hrfeFnKnmVgZDDOxAHgXfgcJSRyiXB</name></friends><friends><id>3549</id><name>NvvhXwWgCSaYijqhxsrxIWrHbBOOIa</name></friends></friends><greeting>hTAIJLspvLr8DJPG3jYh</greeting><favoriteFruit>f6ZsZ3saRGKMBCZLAkiP</favoriteFruit></users></users></Users3>"
        when:
            def obj = readXml(xml, Argument.of(Users3))
        then:
            obj.users.size() == 1
            obj.users[0]._id == "39771757156730064829"
            obj.users[0].tags.size() == 24
            obj.users[0].friends.size() == 21
            !writeXml(obj).contains("<greeting>")
            !writeXml(obj).contains("<favoriteFruit>")
    }

    void "test read/write record"() {
        when:
            def bean = new RecordBean("fizz", "buzz")
            def result = writeXml(bean)
        then:
            result == '<RecordBean><foo>fizz</foo><bar>buzz</bar></RecordBean>'

        when:
            bean = readXml(result, Argument.of(RecordBean))
        then:
            bean.foo() == 'fizz'
            bean.bar() == 'buzz'
    }

    void "test a bean with an extra executable method"() {
        when:
            def bean = new BeanWithExtraMethod()
            bean.name = "Bob"
            def result = writeXml(bean)
        then:
            result == '<BeanWithExtraMethod><name>Bob</name></BeanWithExtraMethod>'

        when:
            bean = readXml(result, Argument.of(BeanWithExtraMethod))
        then:
            bean.name == 'Bob'
    }

    def "round-trip via decodeArbitrary"() {
        given:
            def xml = '<ArbitraryBean><name>hello</name><value>world</value></ArbitraryBean>'
        when:
            def obj = readXml(xml, Argument.of(ArbitraryBean))
        then:
            obj.name == 'hello'
            obj.value == 'world'
            objRepresentationMatches(obj, xml)
    }

    def "NestedOnlyBean - Object field decoded as Map"() {
        given:
            def xml = '<NestedOnlyBean><nested><key>hello</key></nested></NestedOnlyBean>'
        when:
            def obj = readXml(xml, Argument.of(NestedOnlyBean))
        then:
            obj.nested instanceof Map
            (obj.nested as Map).get('key') == 'hello'
    }

    def "ObjectOnlyBean - empty Object field decodes as null"() {
        given:
            def xml = '<ObjectOnlyBean><object></object></ObjectOnlyBean>'
        when:
            def obj = readXml(xml, ObjectOnlyBean)
        then:
            obj.object == null
    }

    def "ItemsOnlyBean - List<Object> is the sole field"() {
        given:
            def xml = '<ItemsOnlyBean><items><items>alpha</items><items>beta</items></items></ItemsOnlyBean>'
        when:
            def obj = readXml(xml, Argument.of(ItemsOnlyBean))
        then:
            obj.items instanceof List
            obj.items == ['alpha', 'beta']
    }

    @Override
    Map<String, String> getProperties() {
//        [
//            "micronaut.serde.xml.xml-write-features.WRITE_NULLS_AS_XSI_NIL": "false"
//        ]
    }
}
