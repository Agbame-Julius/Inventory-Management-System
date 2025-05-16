package com.products.auth;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.products.request.ResetPasswordRequest;
import com.products.utils.HeadersUtil;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.RespondToAuthChallengeRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.RespondToAuthChallengeResponse;

import java.util.HashMap;
import java.util.Map;

import static software.amazon.awssdk.services.cognitoidentityprovider.model.ChallengeNameType.NEW_PASSWORD_REQUIRED;

public class CompleteNewPasswordHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private final ObjectMapper mapper = new ObjectMapper();
    private final String CLIENT_ID = System.getenv("USER_POOL_CLIENT_ID");
    private final String REGION = System.getenv("REGION");

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        response.setHeaders(HeadersUtil.getHeaders());

        try {
            ResetPasswordRequest resetPasswordRequest = mapper.readValue(request.getBody(), ResetPasswordRequest.class);
            var email = resetPasswordRequest.email();
            var newPassword = resetPasswordRequest.newPassword();
            var session = resetPasswordRequest.session();

            if (email == null || newPassword == null || session == null) {
                return response
                        .withStatusCode(400)
                        .withBody("{\"message\": \"email, newPassword, and session are required\"}");
            }

            var challengeResponse = completeNewPassword(email, newPassword, session);

            if (challengeResponse.authenticationResult() != null) {
                Map<String, Object> result = new HashMap<>();
                result.put("message", "Password changed successfully");
                return response
                        .withStatusCode(200)
                        .withBody(mapper.writeValueAsString(result));
            } else {
                return response
                        .withStatusCode(400)
                        .withBody("{\"message\": \"Failed to complete password change\"}");
            }
        } catch (Exception e) {
            return response
                    .withStatusCode(500)
                    .withBody("{\"message\": \"" + e.getMessage() + "\"}");
        }
    }

    private RespondToAuthChallengeResponse completeNewPassword(String email, String newPassword, String session) {
        CognitoIdentityProviderClient cognitoClient = CognitoIdentityProviderClient.builder()
                .region(Region.of(REGION))
                .build();

        Map<String, String> challengeResponses = Map.of(
                "USERNAME", email,
                "NEW_PASSWORD", newPassword
        );

        RespondToAuthChallengeRequest challengeRequest = RespondToAuthChallengeRequest.builder()
                .clientId(CLIENT_ID)
                .challengeName(NEW_PASSWORD_REQUIRED)
                .session(session)
                .challengeResponses(challengeResponses)
                .build();

        return cognitoClient.respondToAuthChallenge(challengeRequest);
    }
}
