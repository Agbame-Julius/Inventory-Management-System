package com.products.response;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ResponseType {
    private static final ObjectMapper mapper = new ObjectMapper();

    public static APIGatewayProxyResponseEvent errorResponse(int status, String message) {
        try {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(status)
                    .withBody(mapper.writeValueAsString(
                            SuccessResponse.builder()
                                    .success(false)
                                    .message(message)
                                    .build()
                    ));
        } catch (JsonProcessingException ex) {
            throw new RuntimeException(ex);
        }
    }

    public static APIGatewayProxyResponseEvent successResponse(int status, String message) {
        try {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(status)
                    .withBody(mapper.writeValueAsString(
                            SuccessResponse.builder()
                                    .success(true)
                                    .message(message)
                                    .build()
                    ));
        } catch (JsonProcessingException ex) {
            throw new RuntimeException(ex);
        }
    }
}
