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

import io.micronaut.core.type.Argument

import java.sql.Time
import java.time.Instant
import java.time.Month
import java.time.MonthDay
import java.time.OffsetDateTime
import java.time.OffsetTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

abstract class GlobalFeaturesSpec extends JsonCompileSpec {

    protected abstract Map<String, Object> writeSingleElementArraysUnwrappedConfig()

    protected abstract Map<String, Object> acceptSingleValueAsArrayConfig()

    protected abstract Map<String, Object> writeSortedMapEntriesConfig()

    protected abstract Map<String, Object> acceptCaseInsensitivePropertiesConfig()

    protected abstract Map<String, Object> readUnknownEnumValuesAsNullConfig()

    protected abstract Map<String, Object> readUnknownEnumValuesUsingDefaultValueConfig()

    protected abstract Map<String, Object> acceptCaseInsensitiveEnumValuesConfig()

    protected abstract Map<String, Object> dateTimestampNanosecondsDisabledConfig()

    protected abstract Map<String, Object> writeDatesWithZoneIdConfig()

    protected abstract Map<String, Object> adjustDatesToContextTimeZoneConfig()

    void "test global collection map and enum features with generated simple bean record and enum"() {
        given:
            def context = buildContext('test.FeatureBean', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import java.util.List;
import java.util.Map;

@Serdeable
enum Choice {
    ALPHA,
    BETA
}

@Serdeable
class FeatureBean {
    private List<String> values;
    private Map<Integer, String> numbers;
    private Choice choice;
    public List<String> getValues() {
        return values;
    }
    public void setValues(List<String> values) {
        this.values = values;
    }
    public Map<Integer, String> getNumbers() {
        return numbers;
    }
    public void setNumbers(Map<Integer, String> numbers) {
        this.numbers = numbers;
    }
    public Choice getChoice() {
        return choice;
    }
    public void setChoice(Choice choice) {
        this.choice = choice;
    }
}

@Serdeable
record FeatureRecord(List<String> values, Map<Integer, String> numbers, Choice choice) {
}
""", [:], writeSingleElementArraysUnwrappedConfig()
                + acceptSingleValueAsArrayConfig()
                + writeSortedMapEntriesConfig()
                + acceptCaseInsensitiveEnumValuesConfig())
            def choice = getEnum(context, 'test.Choice.BETA')
            def numbers = new LinkedHashMap<Integer, String>()
            numbers.put(10, 'ten')
            numbers.put(2, 'two')
            def bean = newInstance(context, 'test.FeatureBean', [
                values: ['alpha'],
                numbers: numbers,
                choice: choice
            ])
            Class<?> recordType = context.classLoader.loadClass('test.FeatureRecord')
            def recordConstructor = recordType.getDeclaredConstructor(List, Map, choice.class)
            recordConstructor.accessible = true
            def record = recordConstructor.newInstance(['alpha'], numbers, choice)

        expect:
            assertSpecificSerdeSelection(context, 'test.FeatureBean', false, false)
            assertSpecificSerdeSelection(context, 'test.FeatureRecord', false, false)
            assertSpecificSerdeSelection(context, 'test.Choice', false, false)

            writeJson(jsonMapper, bean).contains('"values":"alpha"')
            writeJson(jsonMapper, bean).contains('"numbers":{"2":"two","10":"ten"}')
            writeJson(jsonMapper, record).contains('"values":"alpha"')
            writeJson(jsonMapper, record).contains('"numbers":{"2":"two","10":"ten"}')

            def readBean = jsonMapper.readValue('{"values":"alpha","numbers":{"10":"ten","2":"two"},"choice":"beta"}', Argument.of(typeUnderTest.type))
            readBean.values == ['alpha']
            readBean.choice == choice
            def readRecord = jsonMapper.readValue('{"values":"alpha","numbers":{"10":"ten","2":"two"},"choice":"beta"}', Argument.of(recordType))
            readRecord.values() == ['alpha']
            readRecord.choice() == choice

        cleanup:
            context.close()
    }

    void "test global case insensitive properties selects runtime deserializers for simple bean and record"() {
        given:
            def context = buildContext('test.CaseBean', """
package test;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class CaseBean {
    private String value;
    public String getValue() {
        return value;
    }
    public void setValue(String value) {
        this.value = value;
    }
}

@Serdeable
record CaseRecord(String value) {
}
""", [:], acceptCaseInsensitivePropertiesConfig())
            Class<?> recordType = context.classLoader.loadClass('test.CaseRecord')

        expect:
            assertSpecificSerdeSelection(context, 'test.CaseBean', true, false)
            assertSpecificSerdeSelection(context, 'test.CaseRecord', true, false)
            writeJson(jsonMapper, newInstance(context, 'test.CaseBean', [value: 'alpha'])) == '{"value":"alpha"}'
            jsonMapper.readValue('{"VALUE":"alpha"}', typeUnderTest).value == 'alpha'
            jsonMapper.readValue('{"VALUE":"beta"}', Argument.of(recordType)).value() == 'beta'

        cleanup:
            context.close()
    }

    void "test global enum features with generated simple enum"() {
        given:
            def context = buildContext('test.Choice', """
package test;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
enum Choice {
    ALPHA,
    BETA
}
""", true, acceptCaseInsensitiveEnumValuesConfig() + readUnknownEnumValuesAsNullConfig())
            typeUnderTest = Argument.of(context.classLoader.loadClass('test.Choice'))
            def beta = getEnum(context, 'test.Choice.BETA')

        expect:
            assertSpecificSerdeSelection(context, 'test.Choice', true, false)
            writeJson(jsonMapper, beta) == '"BETA"'
            jsonMapper.readValue('"beta"', typeUnderTest) == beta
            jsonMapper.readValue('"GAMMA"', typeUnderTest) == null

        cleanup:
            context.close()
    }

    void "test global write single element arrays unwrapped"() {
        given:
            def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import java.util.List;

@Serdeable
class Test {
    private List<String> value;
    public List<String> getValue() {
        return value;
    }
    public void setValue(List<String> value) {
        this.value = value;
    }
}
""", [
                value: ['alpha']
            ], writeSingleElementArraysUnwrappedConfig())

        expect:
            writeJson(jsonMapper, beanUnderTest) == '{"value":"alpha"}'

        cleanup:
            context.close()
    }

    void "test global accept single value as array"() {
        given:
            def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import java.util.List;

@Serdeable
class Test {
    private List<String> value;
    public List<String> getValue() {
        return value;
    }
    public void setValue(List<String> value) {
        this.value = value;
    }
}
""", [:], acceptSingleValueAsArrayConfig())

        expect:
            jsonMapper.readValue('{"value":"alpha"}', typeUnderTest).value == ['alpha']

        cleanup:
            context.close()
    }

    void "test global sorted map entries"() {
        given:
            def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import java.util.Map;

@Serdeable
class Test {
    private Map<Integer, String> value;
    public Map<Integer, String> getValue() {
        return value;
    }
    public void setValue(Map<Integer, String> value) {
        this.value = value;
    }
}
""", [
                value: [(10): 'ten', (2): 'two']
            ], writeSortedMapEntriesConfig())

        when:
            def json = writeJson(jsonMapper, beanUnderTest)

        then:
            json.contains('"value":{"2":"two","10":"ten"}')

        cleanup:
            context.close()
    }

    void "test global case insensitive properties"() {
        given:
            def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Test {
    private String value;
    public String getValue() {
        return value;
    }
    public void setValue(String value) {
        this.value = value;
    }
}
""", [:], acceptCaseInsensitivePropertiesConfig())

        expect:
            jsonMapper.readValue('{"VALUE":"alpha"}', typeUnderTest).value == 'alpha'

        cleanup:
            context.close()
    }

    void "test global unknown enum values as null"() {
        given:
            def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import org.jspecify.annotations.Nullable;

@Serdeable
enum Choice {
    ALPHA,
    BETA
}

@Serdeable
class Test {
    @Nullable
    private Choice value;
    public Choice getValue() {
        return value;
    }
    public void setValue(Choice value) {
        this.value = value;
    }
}
""", [:], readUnknownEnumValuesAsNullConfig())

        expect:
            jsonMapper.readValue('{"value":"GAMMA"}', typeUnderTest).value == null

        cleanup:
            context.close()
    }

    void "test global unknown enum values using default value"() {
        given:
            def context = buildContext('test.Test', """
package test;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
enum Choice {
    ALPHA,
    BETA,
    @JsonEnumDefaultValue
    UNKNOWN
}

@Serdeable
class Test {
    private Choice value;
    public Choice getValue() {
        return value;
    }
    public void setValue(Choice value) {
        this.value = value;
    }
}
""", [:], readUnknownEnumValuesUsingDefaultValueConfig())
            def defaultValue = getEnum(context, 'test.Choice.UNKNOWN')

        expect:
            jsonMapper.readValue('{"value":"GAMMA"}', typeUnderTest).value == defaultValue

        cleanup:
            context.close()
    }

    void "test global case insensitive enum values"() {
        given:
            def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
enum Choice {
    ALPHA,
    BETA
}

@Serdeable
class Test {
    private Choice value;
    public Choice getValue() {
        return value;
    }
    public void setValue(Choice value) {
        this.value = value;
    }
}
""", [:], acceptCaseInsensitiveEnumValuesConfig())
            def beta = getEnum(context, 'test.Choice.BETA')

        expect:
            jsonMapper.readValue('{"value":"beta"}', typeUnderTest).value == beta

        cleanup:
            context.close()
    }

    void "test global date timestamp nanoseconds features"() {
        given:
            def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import java.time.Instant;

@Serdeable
class Test {
    private Instant value;
    public Instant getValue() {
        return value;
    }
    public void setValue(Instant value) {
        this.value = value;
    }
}
""", [value: Instant.ofEpochSecond(123, 456789123)], dateTimestampNanosecondsDisabledConfig())

        expect:
            writeJson(jsonMapper, beanUnderTest) == '{"value":123456}'
            jsonMapper.readValue('{"value":123456}', typeUnderTest).value == Instant.ofEpochMilli(123456)

        cleanup:
            context.close()
    }

    void "test global date timestamp features for scalar temporal values"() {
        given:
            def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import java.sql.Time;
import java.time.Month;
import java.time.MonthDay;
import java.time.OffsetTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

@Serdeable
class Test {
    private OffsetTime offsetTime;
    private YearMonth yearMonth;
    private MonthDay monthDay;
    private Month month;
    private ZoneOffset zoneOffset;
    private Time sqlTime;
    private TimeZone timeZone;
    private Calendar calendar;
    private GregorianCalendar gregorianCalendar;
    public OffsetTime getOffsetTime() {
        return offsetTime;
    }
    public void setOffsetTime(OffsetTime offsetTime) {
        this.offsetTime = offsetTime;
    }
    public YearMonth getYearMonth() {
        return yearMonth;
    }
    public void setYearMonth(YearMonth yearMonth) {
        this.yearMonth = yearMonth;
    }
    public MonthDay getMonthDay() {
        return monthDay;
    }
    public void setMonthDay(MonthDay monthDay) {
        this.monthDay = monthDay;
    }
    public Month getMonth() {
        return month;
    }
    public void setMonth(Month month) {
        this.month = month;
    }
    public ZoneOffset getZoneOffset() {
        return zoneOffset;
    }
    public void setZoneOffset(ZoneOffset zoneOffset) {
        this.zoneOffset = zoneOffset;
    }
    public Time getSqlTime() {
        return sqlTime;
    }
    public void setSqlTime(Time sqlTime) {
        this.sqlTime = sqlTime;
    }
    public TimeZone getTimeZone() {
        return timeZone;
    }
    public void setTimeZone(TimeZone timeZone) {
        this.timeZone = timeZone;
    }
    public Calendar getCalendar() {
        return calendar;
    }
    public void setCalendar(Calendar calendar) {
        this.calendar = calendar;
    }
    public GregorianCalendar getGregorianCalendar() {
        return gregorianCalendar;
    }
    public void setGregorianCalendar(GregorianCalendar gregorianCalendar) {
        this.gregorianCalendar = gregorianCalendar;
    }
}
""", [
                offsetTime: OffsetTime.of(12, 30, 45, 123456789, ZoneOffset.ofHours(2)),
                yearMonth: YearMonth.of(2024, 9),
                monthDay: MonthDay.of(9, 8),
                month: Month.SEPTEMBER,
                zoneOffset: ZoneOffset.ofHoursMinutes(5, 30),
                sqlTime: Time.valueOf('12:30:45'),
                timeZone: TimeZone.getTimeZone('Europe/Paris'),
                calendar: calendar('Europe/Paris', 123),
                gregorianCalendar: calendar('Europe/Paris', 123)
            ], dateTimestampNanosecondsDisabledConfig())

        expect:
            validateJsonWithoutOrder(
                jsonMapper,
                '{"offsetTime":[12,30,45,123,"+02:00"],"yearMonth":[2024,9],"monthDay":"--09-08","month":9,"zoneOffset":"+05:30","sqlTime":"12:30:45","timeZone":"Europe/Paris","calendar":1725791445123,"gregorianCalendar":1725791445123}',
                writeJson(jsonMapper, beanUnderTest)
            )
            def read = jsonMapper.readValue('{"offsetTime":[13,31,46,987,"+03:00"],"yearMonth":[2025,10],"monthDay":"--10-09","month":10,"zoneOffset":"+02:30","sqlTime":"13:31:46","timeZone":"America/New_York","calendar":1725885045000,"gregorianCalendar":1725885045000}', typeUnderTest)
            read.offsetTime == OffsetTime.of(13, 31, 46, 987000000, ZoneOffset.ofHours(3))
            read.yearMonth == YearMonth.of(2025, 10)
            read.monthDay == MonthDay.of(10, 9)
            read.month == Month.OCTOBER
            read.zoneOffset == ZoneOffset.ofHoursMinutes(2, 30)
            read.sqlTime == Time.valueOf('13:31:46')
            read.timeZone.ID == 'America/New_York'
            read.calendar.timeInMillis == 1725885045000L
            read.gregorianCalendar.timeInMillis == 1725885045000L

        cleanup:
            context.close()
    }

    void "test global write dates with zone id"() {
        given:
            def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import java.time.ZonedDateTime;

@Serdeable
class Test {
    private ZonedDateTime value;
    public ZonedDateTime getValue() {
        return value;
    }
    public void setValue(ZonedDateTime value) {
        this.value = value;
    }
}
""", [
                value: ZonedDateTime.of(2024, 1, 2, 3, 4, 5, 0, ZoneId.of('Europe/Paris'))
            ], writeDatesWithZoneIdConfig())

        when:
            def json = writeJson(jsonMapper, beanUnderTest)
            def tree = jsonMapper.readValue(json, Map)

        then:
            tree.value.contains('[Europe/Paris]')

        cleanup:
            context.close()
    }

    void "test global adjust dates to context timezone"() {
        given:
            def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import java.time.OffsetDateTime;

@Serdeable
class Test {
    private OffsetDateTime value;
    public OffsetDateTime getValue() {
        return value;
    }
    public void setValue(OffsetDateTime value) {
        this.value = value;
    }
}
""", [:], adjustDatesToContextTimeZoneConfig())

        when:
            def read = jsonMapper.readValue('{"value":"2024-01-02T03:04:05+02:00"}', typeUnderTest)

        then:
            read.value == OffsetDateTime.of(2024, 1, 2, 1, 4, 5, 0, ZoneOffset.UTC)

        cleanup:
            context.close()
    }

    private static GregorianCalendar calendar(String timeZoneId, int millis = 0) {
        def calendar = new GregorianCalendar(TimeZone.getTimeZone(timeZoneId))
        calendar.clear()
        calendar.set(2024, Calendar.SEPTEMBER, 8, 12, 30, 45)
        calendar.set(Calendar.MILLISECOND, millis)
        calendar
    }
}
