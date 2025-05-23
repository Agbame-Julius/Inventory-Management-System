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


public class UpdateProductHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final DynamoDbEnhancedClient enhancedClient;
    private final DynamoDbTable<Product> productTable;
    private final ObjectMapper objectMapper;

    public UpdateProductHandler() {
        // Initialize DynamoDB Enhanced Client
        DynamoDbClient dynamoDbClient = DynamoDbClient.builder().build();
        enhancedClient = DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .build();
        // Map to ProductTable using the Product model
        productTable = enhancedClient.table(System.getenv("PRODUCT_TABLE"), TableSchema.fromBean(Product.class));
        // Initialize Jackson ObjectMapper with JavaTimeModule
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        // Disable writing dates as timestamps (optional - formats as ISO-8601 strings)
        objectMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        response.setHeaders(HeadersUtil.getHeaders());

        try {
            if(!CognitoUtil.isAdmin(input)){
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(401)
                        .withBody("You're not authorized to perform this operation");
            }

            // Extract productId from path parameters
            String productId = input.getPathParameters() != null ? input.getPathParameters().get("productId") : null;
            if (productId == null || productId.isEmpty()) {
                response.setStatusCode(400);
                response.setBody("{\"error\": \"productId is required in path parameters\"}");
                return response;
            }

            // Deserialize request body to Product object
            Product updateRequest;
            try {
                updateRequest = objectMapper.readValue(input.getBody(), Product.class);
                context.getLogger().log("Parsed request body: " + updateRequest.toString());
            } catch (JsonProcessingException e) {
                context.getLogger().log("Error parsing request body: " + e.getMessage());
                response.setStatusCode(400);
                response.setBody("{\"error\": \"Invalid request body\"}");
                return response;
            }

            // Fetch existing product to ensure it exists
            Product existingProduct = productTable.getItem(r -> r.key(k -> k.partitionValue(productId)));
            if (existingProduct == null) {
                response.setStatusCode(404);
                response.setBody("{\"error\": \"Product not found\"}");
                return response;
            }

            // Update fields only if provided in the request (merge with existing product)
            // Use productId from path parameter, not from request body
            Product updatedProduct = Product.builder()
                    .productId(productId) // Use path parameter productId
                    .productName(updateRequest.getProductName() != null ? updateRequest.getProductName() : existingProduct.getProductName())
                    .unitCostPrice(updateRequest.getUnitCostPrice() != 0.0 ? updateRequest.getUnitCostPrice() : existingProduct.getUnitCostPrice())
                    .quantity(updateRequest.getQuantity() != 0 ? updateRequest.getQuantity() : existingProduct.getQuantity())
                    .dateAdded(existingProduct.getDateAdded()) // Preserve original dateAdded
                    .dateUpdated(updateRequest.getDateUpdated() != null ? updateRequest.getDateUpdated() : java.time.LocalDate.now())
                    .categoryId(updateRequest.getCategoryId() != null ? updateRequest.getCategoryId() : existingProduct.getCategoryId())
                    .categoryName(updateRequest.getCategoryName() != null ? updateRequest.getCategoryName() : existingProduct.getCategoryName())
                    .totalPrice(updateRequest.getUnitCostPrice() != 0.0 && updateRequest.getQuantity() != 0 ?
                            updateRequest.getUnitCostPrice() * updateRequest.getQuantity() : existingProduct.getTotalPrice())
                    .unitSellingPrice(updateRequest.getUnitSellingPrice() != 0.0 ? updateRequest.getUnitSellingPrice() : existingProduct.getUnitSellingPrice())
                    .totalSellingPrice(updateRequest.getUnitSellingPrice() != 0.0 && updateRequest.getQuantity() != 0 ?
                            updateRequest.getUnitSellingPrice() * updateRequest.getQuantity() : existingProduct.getTotalSellingPrice())
                    .build();
            context.getLogger().log("UpdatedProduct: " + updatedProduct.toString());

            // Update the product in DynamoDB
            productTable.updateItem(updatedProduct);
            context.getLogger().log("Product updated successfully: " + updatedProduct.getProductName());

            // Serialize updated product to JSON
            String responseBody = objectMapper.writeValueAsString(updatedProduct);

            // Set successful response (HTTP 200)
            response.setStatusCode(200);
            response.setBody(responseBody);
        } catch (JsonProcessingException e) {
            context.getLogger().log("Error serializing response: " + e.getMessage());
            response.setStatusCode(500);
            response.setBody("{\"error\": \"Failed to serialize response\"}");
        } catch (DynamoDbException e) {
            context.getLogger().log("Error updating product: " + e.getMessage());
            response.setStatusCode(500);
            response.setBody("{\"error\": \"Failed to update product\"}");
        }

        return response;
    }
}