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
import com.products.request.CreateSalesRequest;
import com.products.request.SaleLineItem;
import com.products.response.SuccessResponse;
import com.products.utils.CognitoUtil;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.time.LocalDate;
import java.util.UUID;

public class CreateSalesHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

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
                return errorResponse(401, "User is not authorized to perform this action");
            }

            if (event.getBody() == null) {
                return errorResponse(400, "Request body is required");
            }

            logger.log("Received request: " + event.getBody());

            CreateSalesRequest request = mapper.readValue(event.getBody(), CreateSalesRequest.class);
            validateRequest(request);

            double totalSalePrice = 0.0;
            var quantitySold = 0;
            for (SaleLineItem item : request.items()) {
                var product = productRepository.findByProductId(item.getProductId());
                if (product == null) {
                    return errorResponse(404, "Product not found: " + item.getProductId());
                }
                if (item.getQuantitySold() > product.getQuantity()) {
                    return errorResponse(400, "Not enough stock for product: " + product.getProductName());
                }
                double expectedTotal = item.getQuantitySold() * item.getUnitPrice();
                if (Math.abs(expectedTotal - item.getTotalPrice()) > 0.01) {
                    return errorResponse(400, "Total price mismatch for product: " + item.getProductId());
                }
                product.setQuantity(product.getQuantity() - item.getQuantitySold());
                product.setDateUpdated(LocalDate.now());
                productRepository.save(product);
                totalSalePrice += item.getTotalPrice();
                quantitySold += item.getQuantitySold();
            }

            Sales sales = Sales.builder()
                    .salesId(UUID.randomUUID().toString())
                    .items(request.items())
                    .totalPrice(totalSalePrice)
                    .quantitySold(quantitySold)
                    .dateSold(LocalDate.now())
                    .dateUpdated(LocalDate.now())
                    .build();

            salesRepository.save(sales);

            return successResponse();
        } catch (Exception e) {
            logger.log("Error: " + e.getMessage());
            return errorResponse(500, "Error processing request: " + e.getMessage());
        }
    }

    private void validateRequest(CreateSalesRequest request) {
        if (request.items() == null || request.items().isEmpty()) {
            throw new IllegalArgumentException("At least one sale line item is required");
        }
        for (SaleLineItem item : request.items()) {
            if (item.getProductId() == null || item.getProductId().isEmpty()) {
                throw new IllegalArgumentException("Product is required for each line item");
            }
            if (item.getQuantitySold() <= 0) {
                throw new IllegalArgumentException("Quantity sold must be greater than 0 for each line item");
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
                    .withStatusCode(201)
                    .withBody(mapper.writeValueAsString(
                            SuccessResponse.builder()
                                    .success(true)
                                    .message("Sales created successfully")
                                    .build()
                    ));
        } catch (JsonProcessingException ex) {
            throw new RuntimeException(ex);
        }
    }
}
