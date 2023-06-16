package io.micronaut.serde.jackson.serdeimport

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent
import io.micronaut.serde.annotation.SerdeImport

@SerdeImport(APIGatewayV2HTTPEvent.RequestContext.Authorizer.class)
@SerdeImport(APIGatewayV2HTTPEvent.RequestContext.Authorizer.JWT.class)
@SerdeImport(APIGatewayV2HTTPEvent.RequestContext.Http.class)
@SerdeImport(APIGatewayV2HTTPEvent.RequestContext.IAM.class)
@SerdeImport(APIGatewayV2HTTPEvent.RequestContext.CognitoIdentity.class)
@SerdeImport(APIGatewayV2HTTPEvent.RequestContext.class)
@SerdeImport(APIGatewayV2HTTPEvent.class)
class APIGatewayV2HTTPEventSerde {

}
