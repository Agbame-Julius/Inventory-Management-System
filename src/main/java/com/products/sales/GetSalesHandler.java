package com.products.sales;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.products.repository.SalesRepository;
import com.products.response.GetSalesResponse;
import com.products.response.ResponseType;
import com.products.utils.CognitoUtil;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.util.Map;

public class GetSalesHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final DynamoDbClient dynamoClient = DynamoDbClient.create();
    private final DynamoDbEnhancedClient enhancedClient = DynamoDbEnhancedClient.builder()
            .dynamoDbClient(dynamoClient)
            .build();
    private final String salesTable = System.getenv("SALES_TABLE");
    private final SalesRepository salesRepository = new SalesRepository(enhancedClient, salesTable);
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {

        try {

            if (!CognitoUtil.isSalesPerson(event) && !CognitoUtil.isAdmin(event))
                return ResponseType.errorResponse(401, "User is not authorized to perform this action");

            Map<String, String> queryParams = event.getQueryStringParameters();
            int limit = getQueryParamAsInt(queryParams);
            String lastEvaluatedKey = getQueryParam(queryParams, "lastEvaluatedKey");

            if (limit <= 0 || limit > 100)
                return ResponseType.errorResponse(400, "Limit must be between 1 and 100");

            var paginatedResult = salesRepository.findAllPaginated(limit, lastEvaluatedKey);
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withBody(mapper.writeValueAsString(
                            GetSalesResponse.builder()
                                    .success(true)
                                    .message("Sales retrieved successfully")
                                    .sales(paginatedResult.getItems())
                                    .lastEvaluatedKey(paginatedResult.getLastEvaluatedKey())
                                    .hasMore(paginatedResult.getLastEvaluatedKey() != null)
                                    .totalReturned(paginatedResult.getItems().size())
                                    .build()
                    ));

        } catch (Exception e) {
            return ResponseType.errorResponse(500, "Error retrieving sales: " + e.getMessage());

        }
    }

    private String getQueryParam(Map<String, String> queryParams, String paramName) {
        if (queryParams == null || !queryParams.containsKey(paramName)) {
            return null;
        }
        return queryParams.get(paramName);
    }

    private int getQueryParamAsInt(Map<String, String> queryParams) {
        String value = getQueryParam(queryParams, "limit");
        if (value == null) {
            return 20;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 20;
        }
    }
}
