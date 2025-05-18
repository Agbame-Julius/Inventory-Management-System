package com.products.sales;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.products.model.Product;
import com.products.model.Sales;
import com.products.repository.ProductRepository;
import com.products.repository.SalesRepository;
import com.products.request.CreateSalesRequest;
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
            if (!CognitoUtil.isSalesPerson(event))
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(401)
                        .withBody(mapper.writeValueAsString(
                                SuccessResponse.builder()
                                        .success(false)
                                        .message("User is not authorized to perform this action")
                                        .build()
                        ));

            if (event.getBody() == null)
                throw new IllegalArgumentException("Request body is required");
            logger.log("Received request: " + event.getBody());

            CreateSalesRequest request = mapper.readValue(event.getBody(), CreateSalesRequest.class);
            if (request != null)
                validateRequest(request);

            var product = productRepository.findByProductIdAndCategoryId(request.productId(), request.categoryId());
            if (product == null)
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(404)
                        .withBody(mapper.writeValueAsString(
                                SuccessResponse.builder()
                                        .success(false)
                                        .message("Product or Category not found")
                                        .build()
                        ));

            checkQuantityAndDeduct(product, request.quantitySold(), request.unitPrice() ,request.totalPrice());

            var sales = Sales.builder()
                    .salesId(UUID.randomUUID().toString())
                    .productId(request.productId())
                    .categoryId(request.categoryId())
                    .quantitySold(request.quantitySold())
                    .unitPrice(request.unitPrice())
                    .totalPrice(request.totalPrice())
                    .dateSold(LocalDate.now())
                    .dateUpdated(LocalDate.now())
                    .build();
            salesRepository.save(sales);
            productRepository.save(product);

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(201)
                    .withBody(mapper.writeValueAsString(
                            SuccessResponse.builder()
                                    .success(true)
                                    .message("Sales created successfully")
                                    .build()
                    ));
        } catch (Exception e) {
            logger.log("Error: " + e.getMessage());
            try {
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(500)
                        .withBody(mapper.writeValueAsString(
                                SuccessResponse.builder()
                                        .success(false)
                                        .message("Error processing request: " + e.getMessage())
                                        .build()
                        ));
            } catch (JsonProcessingException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    private void checkQuantityAndDeduct(Product product, int quantitySold, double unitPrice, double totalPrice) {
        if (quantitySold > product.getQuantity())
            throw new IllegalArgumentException("Quantity sold is greater than quantity in stock");

        var expectedTotalPrice = quantitySold * unitPrice;
        if (expectedTotalPrice != totalPrice)
            throw new IllegalArgumentException("Total price is not correct");

        product.setQuantity(product.getQuantity() - quantitySold);
        product.setDateUpdated(LocalDate.now());
    }

    private void validateRequest(CreateSalesRequest request) {
        if (request.productId() == null || request.productId().isEmpty())
            throw new IllegalArgumentException("Product is required");

        if (request.categoryId() == null || request.categoryId().isEmpty())
            throw new IllegalArgumentException("Category is required");

        if (request.quantitySold() <= 0)
            throw new IllegalArgumentException("Quantity sold must be greater than 0");

        if (request.unitPrice() <= 0)
            throw new IllegalArgumentException("Unit price must be greater than 0");

        if (request.totalPrice() <= 0)
            throw new IllegalArgumentException("Total price must be greater than 0");


    }
}
