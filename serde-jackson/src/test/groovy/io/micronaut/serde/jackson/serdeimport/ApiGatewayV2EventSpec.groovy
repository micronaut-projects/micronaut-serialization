package io.micronaut.serde.jackson.serdeimport

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent
import io.micronaut.core.type.Argument
import io.micronaut.json.JsonMapper

import spock.lang.Specification

import java.lang.reflect.Type
class ApiGatewayV2EventSpec extends Specification {

    void "test deserialization of APIGatewayV2HttpEvent"() {
        given:
        JsonMapper jsonMapper = JsonMapper.createDefault()
        File f = new File("src/test/resources/apiGatewayV2HTTPEvent.json")
        expect:
        f.exists()

        when:
        String json = f.text

        then:
        json

        when:
        APIGatewayV2HTTPEvent event = fromJson(jsonMapper, json, APIGatewayV2HTTPEvent.class)

        then:
        noExceptionThrown()
        "2.0" == event.getVersion()
        '$default' == event.getRouteKey()
        "/" == event.getRawPath()
        "" == event.getRawQueryString()
        "*/*" == event.getHeaders().get("accept")
        "0" == event.getHeaders().get("content-length")
        "y9j2vzg784.execute-api.us-east-1.amazonaws.com" == event.getHeaders().get("host")
        "curl/7.88.1" == event.getHeaders().get("user-agent")
        "Root=1-6488b8c4-28a476ca7ae7e1447b5e2d8f" == event.getHeaders().get("x-amzn-trace-id")
        "80.26.234.66" == event.getHeaders().get("x-forwarded-for")
        "443" == event.getHeaders().get("x-forwarded-port")
        "https" == event.getHeaders().get("x-forwarded-proto")
        "646406757139" == event.getRequestContext().getAccountId()
        "y9j2zvz764" == event.getRequestContext().getApiId()
        "y5j3zzg784.execute-api.us-east-1.amazonaws.com" == event.getRequestContext().getDomainName()
        "y5j3zzg784" == event.getRequestContext().getDomainPrefix()
        "GET" == event.getRequestContext().getHttp().getMethod()
        "/" == event.getRequestContext().getHttp().getPath()
        "HTTP/1.1" == event.getRequestContext().getHttp().getProtocol()
        "80.26.234.66" == event.getRequestContext().getHttp().getSourceIp()
        "curl/7.88.1" == event.getRequestContext().getHttp().getUserAgent()
        "GeHOyjS9oAMEV-A=" == event.getRequestContext().getRequestId()
        '$default' == event.getRequestContext().getRouteKey()
        '$default' == event.getRequestContext().getStage()
        "13/Jun/2023:18:43:16 +0000" == event.getRequestContext().getTime()
        1686681796799L  == event.getRequestContext().getTimeEpoch()
        !event.getIsBase64Encoded()
    }

    <T> T fromJson(JsonMapper jsonMapper, String input, Type type) throws IOException {
        return (T) jsonMapper.readValue(input, Argument.of(type));
    }
}
