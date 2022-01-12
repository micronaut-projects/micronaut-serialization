package io.micronaut.serde.awslambdaevents

import com.amazonaws.services.lambda.runtime.events.CodeCommitEvent
import io.micronaut.context.BeanContext
import io.micronaut.serde.ObjectMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(startApplication = false)
class CodeCommitRepositoryEventSpec extends Specification {

    @Inject
    ObjectMapper objectMapper

    @Inject
    BeanContext beanContext

    void "test deserialization of cloud watch scheduled event"() {
        given:
        File f = new File('src/test/resources/codecommit-repository.json')

        expect:
        f.exists()

        when:
        String json = f.text

        then:
        json

        when:
        CodeCommitEvent event = objectMapper.readValue(json, CodeCommitEvent)

        then:
        event.getRecords()
        event.getRecords().size() == 1

        when:
        CodeCommitEvent.Record record = event.getRecords().get(0)

        then:
        "5a824061-17ca-46a9-bbf9-114edeadbeef" == record.eventId
        "1.0" == record.eventVersion
        "my-trigger" == record.eventTriggerName
        1 == record.eventPartNumber
        new CodeCommitEvent.CodeCommit()
                .withReferences([new CodeCommitEvent.Reference()
                                         .withCommit("5c4ef1049f1d27deadbeeff313e0730018be182b")
                                         .withRef("refs/heads/master")]) == record.codeCommit
        "TriggerEventTest" == record.eventName
        "5a824061-17ca-46a9-bbf9-114edeadbeef" == record.eventTriggerConfigId
        "arn:aws:codecommit:us-east-1:123456789012:my-repo" == record.eventSourceArn
        "arn:aws:iam::123456789012:root" == record.userIdentityArn
        "aws:codecommit" == record.eventSource
        "us-east-1" == record.awsRegion
        "this is custom data" == record.customData
        1 == record.eventTotalParts
        "2016-01-01T23:59:59.000+0000" == record.eventTime.toString()
    }
}
