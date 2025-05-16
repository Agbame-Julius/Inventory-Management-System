package com.products.auth;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.CloudFormationCustomResourceEvent;
import com.products.response.CloudFormationResponseSender;
import com.products.utils.EmailTemplateLoader;
import com.products.utils.PasswordUtil;
import com.products.utils.SESUtil;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminSetUserPasswordRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminSetUserPasswordResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class SetAdminPasswordHandler implements RequestHandler<CloudFormationCustomResourceEvent, Map<String, Object>> {

    private final CognitoIdentityProviderClient cognitoClient;
    private final String adminEmail;
    private final String adminFirstName;
    private final String userPoolId;
    private String emailHtmlTemplate;
    private final String EMAIL_TEMPLATE_PATH = "temporary_password.html";

    public SetAdminPasswordHandler() {
        this.cognitoClient = CognitoIdentityProviderClient.builder()
                .region(Region.of(System.getenv("REGION")))
                .build();
        this.adminEmail = System.getenv("ADMIN_EMAIL");
        this.adminFirstName = System.getenv("ADMIN_FIRST_NAME");
        this.userPoolId = System.getenv("USER_POOL_ID");

        try {
            emailHtmlTemplate = EmailTemplateLoader.loadResourceFile(EMAIL_TEMPLATE_PATH);
        } catch (IOException e) {
            emailHtmlTemplate = "<h2>Hello {{name}},</h2><p>New login detected at {{loginTime}}.</p>";
            System.err.println("Failed to load email template: " + e.getMessage());
        }
    }

    @Override
    public Map<String, Object> handleRequest(CloudFormationCustomResourceEvent event, Context context) {
        String requestType = event.getRequestType();
        Map<String, Object> responseData = new HashMap<>();
        String responseStatus = "SUCCESS";
        String reason = "Operation successful";
        String physicalResourceId = "SetAdminPasswordResource";

        try {
            if ("Create".equalsIgnoreCase(requestType)) {
                var tempPassword = PasswordUtil.generatePassword();

                AdminSetUserPasswordRequest passwordRequest = AdminSetUserPasswordRequest.builder()
                        .userPoolId(userPoolId)
                        .username(adminEmail)
                        .password(tempPassword)
                        .permanent(false)
                        .build();

                AdminSetUserPasswordResponse response = cognitoClient.adminSetUserPassword(passwordRequest);

                sendPasswordEmail(adminEmail, tempPassword);

                responseData.put("Message", "Admin user created successfully with temporary password");
            } else if ("Update".equalsIgnoreCase(requestType)) {
                responseData.put("Message", "Update event received, no action taken.");
            } else if ("Delete".equalsIgnoreCase(requestType)) {
                responseData.put("Message", "Delete event received, no action taken.");
            }
        } catch (Exception e) {
            responseStatus = "FAILED";
            reason = e.getMessage();
            context.getLogger().log("Error: " + e.getMessage());
        }

        try {
            CloudFormationResponseSender.sendResponse(
                    event.getResponseUrl(),
                    responseStatus,
                    reason,
                    physicalResourceId,
                    event.getStackId(),
                    event.getRequestId(),
                    event.getLogicalResourceId(),
                    responseData
            );
        } catch (Exception ex) {
            context.getLogger().log("Failed to send CloudFormation response: " + ex.getMessage());
        }

        return responseData;
    }

    private void sendPasswordEmail(String email, String tempPassword) {
        String subject = "Your Inventory Management System Temporary Password";
        String htmlBody = emailHtmlTemplate
                .replace("{{firstName}}", adminFirstName)
                .replace("{{email}}",  email)
                .replace("{{tempPassword}}", tempPassword);

        SESUtil.sendEmail(email, subject, htmlBody);
    }
}
