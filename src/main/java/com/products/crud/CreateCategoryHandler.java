package com.products.crud;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.products.utils.CognitoUtil;
import com.products.utils.HeadersUtil;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.core.SdkSystemSetting;
import com.products.model.Category;
import com.products.request.CategoryRequest;
import com.products.response.CategoryResponse;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class CreateCategoryHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private final DynamoDbClient dynamoDbClient;
    private final ObjectMapper objectMapper;
    private final String tableName;

    public CreateCategoryHandler() {
        this.dynamoDbClient = DynamoDbClient.builder()
                .region(Region.of(System.getenv(SdkSystemSetting.AWS_REGION.environmentVariable())))
                .build();
        this.objectMapper = new ObjectMapper();
        this.tableName = System.getenv("CATEGORY_TABLE");
    }

    // Constructor for testing
    public CreateCategoryHandler(DynamoDbClient dynamoDbClient, ObjectMapper objectMapper, String tableName) {
        this.dynamoDbClient = dynamoDbClient;
        this.objectMapper = objectMapper;
        this.tableName = tableName;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        try {
            if(!CognitoUtil.isAdmin(input)){
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(401)
                        .withBody("You're not authorized to perform this operation");
            }
            // Parse request body
            CategoryRequest request = objectMapper.readValue(input.getBody(), CategoryRequest.class);
            if (request.getCategoryNames() == null || request.getCategoryNames().isEmpty()) {
                return createResponse(400, new CategoryResponse(0, Collections.emptyList(), "No category names provided"), context);
            }

            // Check for existing categories
            List<String> uniqueCategories = checkDuplicateCategories(request.getCategoryNames());

            // Create new categories
            int addedCount = createCategories(uniqueCategories, context);
            List<String> skippedCategories = request.getCategoryNames().stream()
                    .filter(name -> !uniqueCategories.contains(name))
                    .collect(Collectors.toList());

            CategoryResponse response = new CategoryResponse(addedCount, skippedCategories);
            return createResponse(200, response, context);

        } catch (Exception e) {
            context.getLogger().log("Error: " + e.getMessage());
            return createResponse(500, new CategoryResponse(0, Collections.emptyList(), "Internal server error: " + e.getMessage()), context);
        }
    }

    private List<String> checkDuplicateCategories(List<String> categoryNames) {
        List<String> uniqueCategories = new ArrayList<>();

        for (String name : categoryNames) {
            try {
                QueryRequest queryRequest = QueryRequest.builder()
                        .tableName(tableName)
                        .indexName("CategoryNameIndex")
                        .keyConditionExpression("categoryName = :name")
                        .expressionAttributeValues(
                                Collections.singletonMap(":name", AttributeValue.builder().s(name).build()))
                        .build();

                QueryResponse response = dynamoDbClient.query(queryRequest);
                if (response.items().isEmpty()) {
                    uniqueCategories.add(name);
                }
            } catch (DynamoDbException e) {
                log.error("Error checking duplicate category {}: {}", name, e.getMessage());
                throw e;
            }
        }

        return uniqueCategories;
    }

    private int createCategories(List<String> categoryNames, Context context) {
        int addedCount = 0;

        for (String name : categoryNames) {
            try {
                Category category = Category.builder()
                        .categoryId(UUID.randomUUID().toString())
                        .categoryName(name)
                        .build();

                PutItemRequest putItemRequest = PutItemRequest.builder()
                        .tableName(tableName)
                        .item(toDynamoDbItem(category, context))
                        .build();

                dynamoDbClient.putItem(putItemRequest);
                addedCount++;
                context.getLogger().log("Successfully created category: " + name);

            } catch (DynamoDbException e) {
                context.getLogger().log("Error creating category: " + name + " " + e.getMessage());
                throw e;
            }
        }

        return addedCount;
    }

    private Map<String, AttributeValue> toDynamoDbItem(Category category, Context context) {
       try {
           Map<String, AttributeValue> item = new HashMap<>();
           item.put("categoryId", AttributeValue.builder().s(category.getCategoryId()).build());
           item.put("categoryName", AttributeValue.builder().s(category.getCategoryName()).build());
           context.getLogger().log("Success converting to item");
           return item;
       } catch (Exception e) {
           throw new RuntimeException(e);
       }
    }

    private APIGatewayProxyResponseEvent createResponse(int statusCode, CategoryResponse response, Context context) {
        try {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(statusCode)
                    .withHeaders(HeadersUtil.getHeaders())
                    .withBody(objectMapper.writeValueAsString(response));
        } catch (Exception e) {
            context.getLogger().log("Error creating response: " + e.getMessage());
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withBody("{\"error\": \"Error creating response\"}");
        }
    }

}