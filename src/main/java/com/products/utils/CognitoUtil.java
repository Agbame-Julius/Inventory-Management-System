package com.products.utils;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;

import java.util.HashMap;
import java.util.Map;

public class CognitoUtil {

    public static boolean isAdmin(APIGatewayProxyRequestEvent event) {
        var claims = getClaims(event);
        var cognitoGroup = (String) claims.get("cognito:groups");
        return cognitoGroup != null && cognitoGroup.contains("Admin");
    }

    public static boolean isSalesPerson(APIGatewayProxyRequestEvent event) {
        var claims = getClaims(event);
        var cognitoGroup = (String) claims.get("cognito:groups");
        return cognitoGroup != null && cognitoGroup.contains("SalesPerson");
    }

    public static Map<String, Object> getClaims(APIGatewayProxyRequestEvent event) {
        var authorizer = getAuthorizer(event);

        Map<String, Object> authorizerClaims = getMap(authorizer, "claims");

        var userId = getString(authorizerClaims, "sub");
        var email = getString(authorizerClaims, "email");
        var firstName = getString(authorizerClaims, "custom:firstName");
        var lastName = getString(authorizerClaims, "custom:lastName");
        var cognitoGroup = getString(authorizerClaims, "cognito:groups");

        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("email", email);
        claims.put("firstName", firstName);
        claims.put("lastName", lastName);
        claims.put("cognito:groups", cognitoGroup);

        return claims;
    }

    private static Map<String, Object> getAuthorizer(APIGatewayProxyRequestEvent event) {
        if (event == null || event.getRequestContext() == null)
            throw new IllegalStateException("Request context is null, event: " + event);

        var authorizer = event.getRequestContext().getAuthorizer();
        if (authorizer == null)
            throw new IllegalStateException("User is not authenticated, authorizer is null");
        return authorizer;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        throw new IllegalStateException("Expected a map for key: " + key);
    }

    private static String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }
}
