package com.products.auth;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.products.request.CreateUserRequest;
import com.products.response.SuccessResponse;
import com.products.utils.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

import java.io.IOException;
import java.util.Map;

public class CreateSalesPersonHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final CognitoIdentityProviderClient cognitoClient;
    private final String userPoolId;
    private final String groupName;
    private final Region region;
    private final String EMAIL_TEMPLATE_PATH = "temporary_password.html";
    private final Map<String, String> headers;
    private String emailHtmlTemplate;
    private ObjectMapper mapper = new ObjectMapper();

    public CreateSalesPersonHandler() {
        this.region = Region.of(System.getenv("REGION"));
        this.cognitoClient = CognitoIdentityProviderClient.builder()
                .region(region)
                .build();
        this.userPoolId = System.getenv("USER_POOL_ID");
        this.groupName = System.getenv("GROUP_NAME");
        this.headers = HeadersUtil.getHeaders();

        try {
            emailHtmlTemplate = EmailTemplateLoader.loadResourceFile(EMAIL_TEMPLATE_PATH);
        } catch (IOException e) {
            emailHtmlTemplate = "<h2>Hello {{name}},</h2><p>New login detected at {{loginTime}}.</p>";
            System.err.println("Failed to load email template: " + e.getMessage());
        }
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        try {
            if (!CognitoUtil.isAdmin(event))
                return response
                        .withStatusCode(403)
                        .withHeaders(headers)
                        .withBody(mapper.writeValueAsString(
                                SuccessResponse.builder()
                                        .success(false)
                                        .message("Unauthorized")
                                        .build()
                        ));

            var request = mapper.readValue(event.getBody(), CreateUserRequest.class);

            if (request.email() == null || request.firstName() == null || request.lastName() == null) {
                response
                        .withStatusCode(400)
                        .withBody(mapper.writeValueAsString(
                                SuccessResponse.builder()
                                        .success(false)
                                        .message("Missing required fields: email, firstName, lastName")
                                        .build()
                        ));
                return response;
            }

            String tempPassword = PasswordUtil.generatePassword();

            AdminCreateUserRequest createUserRequest = AdminCreateUserRequest.builder()
                    .userPoolId(userPoolId)
                    .username(request.email())
                    .userAttributes(
                            AttributeType.builder().name("email").value(request.email()).build(),
                            AttributeType.builder().name("email_verified").value("true").build(),
                            AttributeType.builder().name("custom:firstName").value(request.firstName()).build(),
                            AttributeType.builder().name("custom:lastName").value(request.lastName()).build()
                    )
                    .temporaryPassword(tempPassword)
                    .messageAction(MessageActionType.SUPPRESS)
                    .desiredDeliveryMediums(DeliveryMediumType.EMAIL)
                    .build();

            cognitoClient.adminCreateUser(createUserRequest);

            AdminAddUserToGroupRequest addToGroupRequest = AdminAddUserToGroupRequest.builder()
                    .userPoolId(userPoolId)
                    .username(request.email())
                    .groupName(groupName)
                    .build();

            cognitoClient.adminAddUserToGroup(addToGroupRequest);
            sendPasswordEmail(request.email(), tempPassword, request.firstName());

            return response
                    .withStatusCode(201)
                    .withBody(
                           mapper.writeValueAsString(
                                   SuccessResponse.builder()
                                           .success(true)
                                           .message("SalesPerson user created successfully.")
                                           .build()
                           ));
        } catch (Exception e) {
            response
                    .withStatusCode(500)
                    .withBody("Failed to create SalesPerson: " + e.getMessage());
            return response;
        }
    }

    private void sendPasswordEmail(String email, String tempPassword, String firstName) {
        String subject = "Your Inventory Management System Temporary Password";
        String htmlBody = emailHtmlTemplate
                .replace("{{firstName}}", firstName)
                .replace("{{email}}",  email)
                .replace("{{tempPassword}}", tempPassword);

        SESUtil.sendEmail(email, subject, htmlBody);
    }
}
