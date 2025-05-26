package com.products.crud;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.products.model.Product;
import com.products.utils.CognitoUtil;
import com.products.utils.HeadersUtil;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GetAllProductsHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final DynamoDbEnhancedClient enhancedClient;
    private final DynamoDbTable<Product> productTable;
    private final ObjectMapper objectMapper;

    public GetAllProductsHandler() {
        // Initialize DynamoDB Enhanced Client
        DynamoDbClient dynamoDbClient = DynamoDbClient.builder().build();
        enhancedClient = DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .build();
        // Map to ProductTable using the Product model
        productTable = enhancedClient.table(System.getenv("PRODUCT_TABLE"), TableSchema.fromBean(Product.class));
        // Initialize Jackson ObjectMapper for JSON serialization
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        response.setHeaders(HeadersUtil.getHeaders());

        try {
            if(!CognitoUtil.isAdmin(input)){
                response.setStatusCode(401);
                response.setBody("You're not authorized to perform this operation");
                return response;
            }
            // Scan the ProductTable to retrieve all items
            List<Product> products = productTable.scan()
                    .items()
                    .stream()
                    .collect(Collectors.toList());

            // Serialize the product list to JSON
            String responseBody = objectMapper.writeValueAsString(products);

            // Set successful response (HTTP 200)
            response.setStatusCode(200);
            response.setBody(responseBody);
        } catch (JsonProcessingException e) {
            // Handle JSON serialization error
            context.getLogger().log("Error serializing products: " + e.getMessage());
            response.setStatusCode(500);
            response.setBody("{\"error\": \"Failed to serialize response\"}");
        } catch (DynamoDbException e) {
            // Handle DynamoDB errors
            context.getLogger().log("Error retrieving products: " + e.getMessage());
            response.setStatusCode(500);
            response.setBody("{\"error\": \"Failed to retrieve products\"}");
        }

        return response;
    }
}
