package io.micronaut.serde.toml

import io.micronaut.serde.toml.fixture.FiveMinuteUser
import io.micronaut.serde.toml.fixture.MediaItem
import io.micronaut.serde.toml.fixture.Point
import io.micronaut.serde.toml.fixture.PointListBean
import io.micronaut.serde.toml.fixture.Rectangle
import io.micronaut.serde.toml.fixture.TemporalValues

import java.io.ByteArrayInputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

abstract class AbstractTomlExtendedSerdeSpec extends AbstractTomlBasicSerdeSpec {

    void "reads equivalent object graphs from string bytes and streams"() {
        given:
        def expected = [foo: "bar", nested: [foo: 4]]
        def toml = "foo = 'bar'\n[nested]\nfoo = 4\n"
        def bytes = tomlAsBytes(toml)

        expect:
        readTomlObject(toml) == expected
        readTomlObject(bytes) == expected
        readTomlObject(new ByteArrayInputStream(bytes)) == expected
    }

    void "serializes quoted string variants deterministically"() {
        expect:
        writeToml([abc: input]) == expected

        where:
        input   | expected
        "foo"   | "abc = 'foo'\n"
        "foo'"  | "abc = \"foo'\"\n"
        'foo"'  | "abc = 'foo\"'\n"
        'foo"\'' | "abc = \"foo\\\"'\"\n"
    }

    void "serializes structural values deterministically"() {
        expect:
        writeToml(value) == expected

        where:
        value                                  | expected
        [abc: 123]                             | "abc = 123\n"
        [abc: true]                            | "abc = true\n"
        [abc: 1.23d]                           | "abc = 1.23\n"
        [abc: [:]]                             | "abc = {}\n"
        [abc: [foo: 1, bar: 2]]                | "abc.foo = 1\nabc.bar = 2\n"
        [abc: []]                              | "abc = []\n"
        [abc: [1, 2, 3]]                       | "abc = [1, 2, 3]\n"
        [abc: [1, [foo: 1, bar: 2]]]           | "abc = [1, {foo = 1, bar = 2}]\n"
        ["foo bar": 123]                       | "'foo bar' = 123\n"
        [abc: "foo\u0001"]                     | "abc = \"foo\\u0001\"\n"
        [abc: "foo\b"]                         | "abc = \"foo\\b\"\n"
    }

    void "serializes temporal values deterministically"() {
        given:
        def values = new TemporalValues()
        values.localDate = LocalDate.of(2021, 3, 27)
        values.localTime = LocalTime.of(18, 40, 15, 123456789)
        values.localDateTime = LocalDateTime.of(2021, 3, 27, 18, 40, 15, 123456789)
        values.offsetDateTime = OffsetDateTime.of(2021, 3, 27, 18, 40, 15, 123456789, ZoneOffset.ofHoursMinutes(1, 23))

        expect:
        writeToml(values) == """localDate = '2021-03-27'
localTime = '18:40:15.123456789'
localDateTime = '2021-03-27T18:40:15.123456789'
offsetDateTime = '2021-03-27T18:40:15.123456789+01:23'
"""
    }

    void "writes nested objects consistently to strings and bytes"() {
        given:
        def rectangle = new Rectangle(new Point(19, 72), new Point(5, 10))

        expect:
        writeTomlAsBytes(rectangle) == tomlAsBytes("""topLeft.x = 19
topLeft.y = 72
bottomRight.x = 5
bottomRight.y = 10
""")
        readToml(writeToml(rectangle), Rectangle) == rectangle
        readToml(writeTomlAsBytes(rectangle), Rectangle) == rectangle
        readToml(new ByteArrayInputStream(writeTomlAsBytes(rectangle)), Rectangle) == rectangle
    }

    void "parses parser-style key and comment cases semantically"() {
        expect:
        objRepresentationMatches(readTomlObject(toml), toml)

        where:
        toml << [
            "# This is a full-line comment\nkey = \"value\"  # This is a comment at the end of a line\nanother = \"# This is not a comment\"\n",
            "key = \"value\"\nbare_key = \"value\"\nbare-key = \"value\"\n1234 = \"value\"\n",
            "\"127.0.0.1\" = \"value\"\n\"character encoding\" = \"value\"\n\"ʎǝʞ\" = \"value\"\n'key2' = \"value\"\n'quoted \"value\"' = \"value\"\n",
            "\"\" = \"blank\"\n",
            "name = \"Orange\"\nphysical.color = \"orange\"\nphysical.shape = \"round\"\nsite.\"google.com\" = true\n",
            "fruit.name = \"banana\"\nfruit. color = \"yellow\"\nfruit . flavor = \"banana\"\n",
            "3.14159 = \"pi\"\n"
        ]
    }

    void "parses string forms semantically"() {
        expect:
        objRepresentationMatches(readTomlObject(toml), toml)

        where:
        toml << [
            "str = \"I'm a string. \\\"You can quote me\\\". Name\\tJos\\u00E9\\nLocation\\tSF.\"\n",
            '''str1 = """
Roses are red
Violets are blue"""
''',
            $/str1 = "The quick brown fox jumps over the lazy dog."

str2 = """
The quick brown \


  fox jumps over \
    the lazy dog."""

str3 = """\
       The quick brown \
       fox jumps over \
       the lazy dog.\
       """
/$,
            $/winpath = 'C:\Users\nodejs\templates'
winpath2 = '\\ServerX\admin$\system32\'
quoted = 'Tom "Dubs" Preston-Werner'
regex = '<\i\c*\s*>'
/$,
            """regex2 = '''I [dw]on't need \\d{2} apples'''
lines = '''
The first newline is
trimmed in raw strings.
   All other whitespace
   is preserved.
'''
""",
            "foo = \"\\U0001f192\"\n"
        ]
    }

    void "parses numeric boolean and temporal values semantically"() {
        expect:
        objRepresentationMatches(readTomlObject(toml), toml)

        where:
        toml << [
            "int1 = +99\nint2 = 42\nint3 = 0\nint4 = -17\n",
            "int5 = 1_000\nint6 = 5_349_221\nint7 = 53_49_221\nint8 = 1_2_3_4_5\n",
            "hex1 = 0xDEADBEEF\nhex2 = 0xdeadbeef\nhex3 = 0xdead_beef\noct1 = 0o01234567\noct2 = 0o755\nbin1 = 0b11010110\n",
            "flt1 = +1.0\nflt2 = 3.1415\nflt3 = -0.01\nflt4 = 5e+22\nflt5 = 1e06\nflt6 = -2E-2\nflt7 = 6.626e-34\n",
            "flt8 = 224_617.445_991_228\n",
            "bool1 = true\nbool2 = false\n",
            "odt1 = 1979-05-27T07:32:00Z\nodt2 = 1979-05-27T00:32:00-07:00\nodt3 = 1979-05-27T00:32:00.999999-07:00\nodt4 = 1979-05-27 07:32:00Z\n",
            "ldt1 = 1979-05-27T07:32:00\nldt2 = 1979-05-27T00:32:00.999999\n",
            "ld1 = 1979-05-27\n",
            "lt1 = 07:32:00\nlt2 = 00:32:00.999999\n"
        ]
    }

    void "parses special floats into infinity and nan values"() {
        when:
        def values = readTomlObject("sf1 = inf\nsf2 = +inf\nsf3 = -inf\nsf4 = nan\nsf5 = +nan\nsf6 = -nan\n")

        then:
        Double.isInfinite((values.sf1 as Number).doubleValue())
        (values.sf1 as Number).doubleValue() > 0d
        Double.isInfinite((values.sf2 as Number).doubleValue())
        (values.sf2 as Number).doubleValue() > 0d
        Double.isInfinite((values.sf3 as Number).doubleValue())
        (values.sf3 as Number).doubleValue() < 0d
        Double.isNaN((values.sf4 as Number).doubleValue())
        Double.isNaN((values.sf5 as Number).doubleValue())
        Double.isNaN((values.sf6 as Number).doubleValue())
    }

    void "parses arrays tables and inline structures semantically"() {
        expect:
        objRepresentationMatches(readTomlObject(toml), toml)

        where:
        toml << [
            $/integers = [ 1, 2, 3 ]
colors = [ "red", "yellow", "green" ]
nested_arrays_of_ints = [ [ 1, 2 ], [3, 4, 5] ]
nested_mixed_array = [ [ 1, 2 ], ["a", "b", "c"] ]
string_array = [ "all", 'strings', """are the same""", '''type''' ]
numbers = [ 0.1, 0.2, 0.5, 1, 2, 5 ]
contributors = [
  "Foo Bar <foo@example.com>",
  { name = "Baz Qux", email = "bazqux@example.com", url = "https://example.com/bazqux" }
]
/$,
            """integers2 = [
  1, 2, 3
]
integers3 = [
  1,
  2,
]
""",
            "[table]\n",
            "[table-1]\nkey1 = \"some string\"\nkey2 = 123\n\n[table-2]\nkey1 = \"another string\"\nkey2 = 456\n",
            "[dog.\"tater.man\"]\ntype.name = \"pug\"\n",
            "[a.b.c]\n[ d.e.f ]\n[ g . h . i ]\n[ j . \"ʞ\" . 'l' ]\n",
            "name = \"Fido\"\nbreed = \"pug\"\n[owner]\nname = \"Regina Dogman\"\n",
            "fruit.apple.color = \"red\"\nfruit.apple.taste.sweet = true\n",
            "[fruit]\napple.color = \"red\"\napple.taste.sweet = true\n[fruit.apple.texture]\nsmooth = true\n",
            "name = { first = \"Tom\", last = \"Preston-Werner\" }\npoint = { x = 1, y = 2 }\nanimal = { type.name = \"pug\" }\n",
            "[[products]]\nname = \"Hammer\"\nsku = 738594937\n\n[[products]]\n\n[[products]]\nname = \"Nail\"\nsku = 284758393\ncolor = \"gray\"\n",
            """[[fruits]]
name = "apple"

[fruits.physical]
color = "red"
shape = "round"

[[fruits.varieties]]
name = "red delicious"

[[fruits.varieties]]
name = "granny smith"

[[fruits]]
name = "banana"

[[fruits.varieties]]
name = "plantain"
""",
            """points = [ { x = 1, y = 2, z = 3 },
           { x = 7, y = 8, z = 9 },
           { x = 2, y = 4, z = 8 } ]
""",
            "foo = {}\n"
        ]
    }

    void "round trips additional pojo fixtures"() {
        given:
        def user = new FiveMinuteUser("Bob", "Palmer", FiveMinuteUser.Gender.MALE, true, [1, 2, 3, 4] as byte[])
        def points = new PointListBean(["a", "b"], [new Point(1, 2), new Point(3, 4)])
        def media = new MediaItem()
        def content = new MediaItem.MediaContent()
        content.title = "Databind test"
        content.format = "jpeg"
        content.width = 900
        content.height = 120
        content.bitrate = 256000
        content.duration = 3600 * 1000L
        content.copyright = "none"
        content.player = MediaItem.MediaContent.Player.FLASH
        content.uri = "http://whatever.biz"
        content.addPerson("William")
        content.addPerson("Robert")
        media.content = content
        media.addPhoto(new MediaItem.Image("http://a.com", "title1", 200, 100, MediaItem.Image.Size.LARGE))
        media.addPhoto(new MediaItem.Image("http://b.org", "title2", 640, 480, MediaItem.Image.Size.SMALL))

        expect:
        roundTrip(user) == user
        roundTrip(points) == points
        roundTrip(media) == media
    }

    void "handles large strings across string bytes and stream inputs"() {
        given:
        def testValue = ("a" * 4000) + "\u5496"
        def toml = "test = \"${testValue}\"\n"
        def bytes = tomlAsBytes(toml)

        expect:
        readTomlObject(toml).test == testValue
        readTomlObject(bytes).test == testValue
        readTomlObject(new ByteArrayInputStream(bytes)).test == testValue
    }

    void "rejects additional invalid parser structures"() {
        when:
        readTomlObject(toml)

        then:
        def e = thrown(Exception)
        messageMatchers.any { matcher -> e.message.contains(matcher) }

        where:
        toml | messageMatchers
        "spelling = \"favorite\"\n\"spelling\" = \"favourite\"\n" | ["Duplicate key"]
        "[fruit]\napple = \"red\"\n\n[fruit]\norange = \"orange\"\n" | ["Table redefined"]
        "[fruit]\napple = \"red\"\n\n[fruit.apple]\ntexture = \"smooth\"\n" | ["Path into existing non-object value", "Table redefined"]
        "fruit.apple.color = \"red\"\n[fruit]\nfoo = \"bar\"\n" | ["Table redefined"]
        "[fruit]\napple.color = \"red\"\napple.taste.sweet = true\n\n[fruit.apple]\n" | ["Table redefined"]
        "[product]\ntype = { name = \"Nail\" }\ntype.edible = false\n" | ["Object already closed", "Duplicate key"]
        "[product]\ntype.name = \"Nail\"\ntype = { edible = false }\n" | ["Duplicate key"]
        "foo = {bar = 'baz',}\n" | ["Trailing comma not permitted for inline tables"]
        "foo = {bar = 'baz',\na = 'b'}\n" | ["Newline not permitted here"]
        "[fruit.physical]\ncolor = \"red\"\nshape = \"round\"\n\n[[fruit]]\nname = \"apple\"\n" | ["Path into existing non-array value", "Path into existing non-object value"]
        "fruits = []\n\n[[fruits]]\n" | ["Array already finished", "Duplicate key"]
        "[[fruits]]\nname = \"apple\"\n\n[[fruits.varieties]]\nname = \"red delicious\"\n\n[fruits.varieties]\nname = \"granny smith\"\n" | ["Path into existing non-object value of type ARRAY", "Path into existing non-object value"]
        "[[fruits]]\nname = \"apple\"\n\n[fruits.physical]\ncolor = \"red\"\nshape = \"round\"\n\n[[fruits.physical]]\ncolor = \"green\"\n" | ["Path into existing non-array value of type OBJECT", "Path into existing non-array value"]
        "foo = 01\n" | ["Zero-prefixed ints are not valid"]
        "foo = +0b1\n" | ["More data after value has already ended", "Unknown token"]
        "foo = # bar\n" | ["Comment not permitted here"]
        "foo = \"\\k\"\n" | ["Unknown escape sequence"]
        "foo = \"\\Uffffffff\"\n" | ["Invalid code point"]
        "invalid_float_1 = .7\n" | ["Unknown token"]
        "invalid_float_2 = 7.\n" | ["More data after value has already ended"]
        "invalid_float_3 = 3.e+20\n" | ["More data after value has already ended"]
        "a = '\u007F'\n" | ["Illegal control character"]
    }
}
