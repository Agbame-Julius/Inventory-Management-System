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
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GetProductsByCategoryHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent>{
    private final DynamoDbEnhancedClient enhancedClient;
    private final DynamoDbTable<Product> productTable;
    private final ObjectMapper objectMapper;

    public GetProductsByCategoryHandler() {
        DynamoDbClient ddb = DynamoDbClient.builder().build();
        this.enhancedClient = DynamoDbEnhancedClient.builder()
                .dynamoDbClient(ddb)
                .build();
        this.productTable = enhancedClient.table(System.getenv("PRODUCT_TABLE"), TableSchema.fromBean(Product.class));
        this.objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }
    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        APIGatewayProxyResponseEvent responseEvent = new APIGatewayProxyResponseEvent();
        responseEvent.setHeaders(HeadersUtil.getHeaders());

        try{
            if(!CognitoUtil.isAdmin(request)){
                responseEvent.setStatusCode(401);
                responseEvent.setBody("You're not authorized to perform this operation");
                return responseEvent;
            }
            Map<String, String> queryParams = request.getQueryStringParameters();
            String categoryId = null;
            String categoryName = null;
            if(queryParams != null){
                categoryId = queryParams.get("categoryId");
                categoryName = queryParams.get("categoryName");
            }
            if(categoryId == null || categoryId.isEmpty() && categoryName == null || categoryName.isEmpty()){
                responseEvent.setStatusCode(400);
                responseEvent.setBody("No category name or id provided");
                return responseEvent;
            }

            Expression filterExpression = buildFilterExpression(categoryId, categoryName);
            ScanEnhancedRequest scanRequest = ScanEnhancedRequest.builder()
                    .filterExpression(filterExpression)
                    .build();
            List<Product> products = productTable.scan(scanRequest)
                    .items()
                    .stream()
                    .toList();
            String  responseBody = objectMapper.writeValueAsString(products);
            responseEvent.setStatusCode(200);
            responseEvent.setBody(responseBody);

        } catch (JsonProcessingException e) {
            context.getLogger().log("Error serializing products: " + e.getMessage());
            responseEvent.setStatusCode(500);
            responseEvent.setBody("Failed to serialize response: " + e.getMessage());
        } catch (DynamoDbException e){
            context.getLogger().log("Error retrieving products: " + e.getMessage());
            responseEvent.setStatusCode(500);
            responseEvent.setBody("Failed to retrieve products: " + e.getMessage());
        } catch (Exception e){
            context.getLogger().log("Unexpected error: " + e.getMessage());
            responseEvent.setStatusCode(500);
            responseEvent.setBody("Unexpected error: " + e.getMessage());
        }
        return responseEvent;
    }



    private Expression buildFilterExpression(String categoryId, String categoryName) {
        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        StringBuilder filterExpression = new StringBuilder();

        if (categoryId != null && !categoryId.isEmpty()) {
            filterExpression.append("categoryId = :categoryId");
            expressionAttributeValues.put(":categoryId", AttributeValue.builder().s(categoryId).build());
        }

        if (categoryName != null && !categoryName.isEmpty()) {
            if (!filterExpression.isEmpty()) {
                filterExpression.append(" AND ");
            }
            filterExpression.append("categoryName = :categoryName");
            expressionAttributeValues.put(":categoryName", AttributeValue.builder().s(categoryName).build());
        }

        return Expression.builder()
                .expression(filterExpression.toString())
                .expressionValues(expressionAttributeValues)
                .build();
    }
}
