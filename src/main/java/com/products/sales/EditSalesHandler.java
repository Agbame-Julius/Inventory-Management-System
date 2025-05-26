package com.products.sales;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.products.model.Sales;
import com.products.repository.ProductRepository;
import com.products.repository.SalesRepository;
import com.products.request.EditSalesRequest;
import com.products.request.SaleLineItem;
import com.products.response.ResponseType;
import com.products.response.SuccessResponse;
import com.products.utils.CognitoUtil;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Logic for Edit/Update existing sales
/**
 * Fetch the existing sales record
 * Create a map of existing items by productId for easy lookup (for merging and not to override the existing sales record)
 * Create a map of request items by productId (same reason)
 * Restore inventory for products that will be updated
 * Create the merged items list
 * Add items from the existing sales that are not in the request (keep them unchanged)
 * Process the new/updated items from the request
 * First, calculate the totals from items we're keeping unchanged
 * Process and add the updated items
 * Validate the total price
 * Deduct the new quantity from inventory
 * Add to totals
 * Add the new/updated item to merged list
 * Update the sales record with the merged items */

public class EditSalesHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final DynamoDbClient dynamoClient = DynamoDbClient.create();
    private final DynamoDbEnhancedClient enhancedClient = DynamoDbEnhancedClient.builder()
            .dynamoDbClient(dynamoClient)
            .build();
    private final String salesTable = System.getenv("SALES_TABLE");
    private final String productTable = System.getenv("PRODUCT_TABLE");
    private final SalesRepository salesRepository = new SalesRepository(enhancedClient, salesTable);
    private final ProductRepository productRepository = new ProductRepository(enhancedClient, productTable);
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        var logger = context.getLogger();
        try {
            if (!CognitoUtil.isSalesPerson(event)) {
                return ResponseType.errorResponse(401, "User is not authorized to perform this action");
            }

            if (event.getBody() == null) {
                return ResponseType.errorResponse(400, "Request body is required");
            }

            logger.log("Received request: " + event.getBody());

            EditSalesRequest request = mapper.readValue(event.getBody(), EditSalesRequest.class);
            var pathParameters = event.getPathParameters();
            var salesId = pathParameters.get("salesId");
            validateRequest(request, salesId);

            Sales existingSales = salesRepository.findBySalesId(salesId);
            if (existingSales == null)
                return errorResponse(404, "Sales record not found");

            var sevenDaysBefore = LocalDate.now().minusDays(7);
            if (!sevenDaysBefore.isBefore(existingSales.getDateSold()))
                throw new IllegalArgumentException("Sales can't be updated as it was made over a week ago.");

            Map<String, SaleLineItem> existingItemsMap = new HashMap<>();
            if (existingSales.getItems() != null) {
                for (SaleLineItem item : existingSales.getItems()) {
                    existingItemsMap.put(item.getProductId(), item);
                }
            }

            Map<String, SaleLineItem> requestItemsMap = new HashMap<>();
            for (SaleLineItem item : request.items()) {
                requestItemsMap.put(item.getProductId(), item);
            }

            for (SaleLineItem requestItem : request.items()) {
                String productId = requestItem.getProductId();
                if (existingItemsMap.containsKey(productId)) {
                    var product = productRepository.findByProductId(productId);
                    if (product != null) {
                        SaleLineItem oldItem = existingItemsMap.get(productId);
                        product.setQuantity(product.getQuantity() + oldItem.getQuantitySold());
                        productRepository.save(product);
                    }
                }
            }

            List<SaleLineItem> mergedItems = new ArrayList<>();

            for (SaleLineItem existingItem : existingSales.getItems()) {
                if (!requestItemsMap.containsKey(existingItem.getProductId())) {
                    mergedItems.add(existingItem);
                }
            }

            double totalSalePrice = 0.0;
            int totalQuantitySold = 0;

            for (SaleLineItem item : mergedItems) {
                totalSalePrice += item.getTotalPrice();
                totalQuantitySold += item.getQuantitySold();
            }

            for (SaleLineItem newItem : request.items()) {
                var product = productRepository.findByProductId(newItem.getProductId());
                if (product == null) {
                    return errorResponse(404, "Product not found: " + newItem.getProductId());
                }

                if (newItem.getQuantitySold() > product.getQuantity()) {
                    return errorResponse(400, "Not enough stock for product: " + newItem.getProductId());
                }

                double expectedTotal = newItem.getQuantitySold() * product.getUnitPrice();
                if (Math.abs(expectedTotal - newItem.getTotalPrice()) > 0.01) {
                    return errorResponse(400, "Total price mismatch for product: " + newItem.getProductId());
                }

                product.setQuantity(product.getQuantity() - newItem.getQuantitySold());
                product.setDateUpdated(LocalDate.now());
                productRepository.save(product);

                totalSalePrice += newItem.getTotalPrice();
                totalQuantitySold += newItem.getQuantitySold();

                mergedItems.add(newItem);
            }

            existingSales.setItems(mergedItems);
            existingSales.setTotalPrice(totalSalePrice);
            existingSales.setDateUpdated(LocalDate.now());
            existingSales.setQuantitySold(totalQuantitySold);
            salesRepository.save(existingSales);

            return successResponse();
        } catch (Exception e) {
            logger.log("Error: " + e.getMessage());
            return errorResponse(500, "Error processing request: " + e.getMessage());
        }
    }

    private void validateRequest(EditSalesRequest request, String salesId) {
        if (salesId == null || salesId.isEmpty()) {
            throw new IllegalArgumentException("Sales ID is required");
        }
        if (request.items() == null || request.items().isEmpty()) {
            throw new IllegalArgumentException("At least one sale line item is required");
        }
        for (SaleLineItem item : request.items()) {
            if (item.getProductId() == null || item.getProductId().isEmpty()) {
                throw new IllegalArgumentException("Product is required for each line item");
            }
            if (item.getQuantitySold() < 0) {
                throw new IllegalArgumentException("Quantity sold must be zero or greater for each line item");
            }
            if (item.getTotalPrice() < 0) {
                throw new IllegalArgumentException("Total price must be zero or greater for each line item");
            }
        }
    }

    private APIGatewayProxyResponseEvent errorResponse(int status, String message) {
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

    private APIGatewayProxyResponseEvent successResponse() {
        try {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withBody(mapper.writeValueAsString(
                            SuccessResponse.builder()
                                    .success(true)
                                    .message("Sales updated successfully")
                                    .build()
                    ));
        } catch (JsonProcessingException ex) {
            throw new RuntimeException(ex);
        }
    }
}