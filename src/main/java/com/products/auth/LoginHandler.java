package com.products.auth;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.products.request.LoginRequest;
import com.products.response.LoginResponse;
import com.products.utils.HeadersUtil;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthRequest;

import java.util.Map;

import static software.amazon.awssdk.services.cognitoidentityprovider.model.AuthFlowType.USER_PASSWORD_AUTH;
import static software.amazon.awssdk.services.cognitoidentityprovider.model.ChallengeNameType.NEW_PASSWORD_REQUIRED;

public class LoginHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private final CognitoIdentityProviderClient cognitoClient = CognitoIdentityProviderClient.builder()
            .region(Region.of(System.getenv("REGION")))
            .build();
    private final String CLIENT_ID = System.getenv("USER_POOL_CLIENT_ID");
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, String> headers = HeadersUtil.getHeaders();


    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {

        try {
            LoginRequest request = mapper.readValue(input.getBody(), LoginRequest.class);
            var email = request.email();
            var password = request.password();

            var authParams = Map.of(
                    "USERNAME", email,
                    "PASSWORD", password
            );

            var authRequest = InitiateAuthRequest.builder()
                    .clientId(CLIENT_ID)
                    .authFlow(USER_PASSWORD_AUTH)
                    .authParameters(authParams)
                    .build();

            long start = System.currentTimeMillis();
            var authResponse = cognitoClient.initiateAuth(authRequest);
            long end = System.currentTimeMillis();
            context.getLogger().log("Cognito initiateAuth took: " + (end - start) + "ms");
            context.getLogger().log(authResponse.toString());

            if (authResponse.challengeName() != null &&
                    authResponse.challengeName().equals(NEW_PASSWORD_REQUIRED))
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(200)
                        .withHeaders(headers)
                        .withBody(mapper.writeValueAsString(
                                LoginResponse.builder()
                                        .success(false)
                                        .message("New password required")
                                        .challengeName(authResponse.challengeName().toString())
                                        .session(authResponse.session())
                                        .build()
                        ));

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withHeaders(headers)
                    .withBody(mapper.writeValueAsString(
                            LoginResponse.builder()
                                    .success(true)
                                    .message("Login successful")
                                    .idToken(authResponse.authenticationResult().idToken())
                                    .role(extractCognitoGroups(authResponse.authenticationResult().idToken()))
                                    .build()
                    ));

        } catch (Exception e) {
            LoginResponse response = LoginResponse.builder()
                    .success(false)
                    .message("Authentication failed: " + e.getMessage())
                    .build();

            try {
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(401)
                        .withHeaders(headers)
                        .withBody(mapper.writeValueAsString(response));
            } catch (JsonProcessingException ex) {
                throw new RuntimeException(ex);
            }
        }

    }


    private String extractCognitoGroups(String idToken) {
        try {
            DecodedJWT jwt = JWT.decode(idToken);

            var claim = jwt.getClaim("cognito:groups");
            if (claim.isNull()) return null;

            var groupsList = claim.asList(String.class);
            if (groupsList != null && !groupsList.isEmpty())
                return String.join(",", groupsList);

            return claim.asString();
        } catch (Exception e) {
            return null;
        }
    }
}
