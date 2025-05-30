package com.products.sales;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.products.mapper.ProductMapper;
import com.products.model.Sales;
import com.products.repository.SalesRepository;
import com.products.response.CustomSalesResponse;
import com.products.response.FilterSalesResponse;
import com.products.response.ResponseType;
import com.products.utils.HeadersUtil;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.time.LocalDate;
import java.util.List;

public class FilterSalesByDateHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final DynamoDbClient dynamoDbClient = DynamoDbClient.create();
    private final DynamoDbEnhancedClient enhancedClient = DynamoDbEnhancedClient.builder()
            .dynamoDbClient(dynamoDbClient)
            .build();
    private final String salesTable = System.getenv("SALES_TABLE");
    private final SalesRepository salesRepository = new SalesRepository(enhancedClient, salesTable);
    private final ProductMapper productMapper = new ProductMapper(enhancedClient);
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {

        try {
            var params = event.getQueryStringParameters();
            String startDateStr = params.get("startDate");
            String endDateStr = params.get("endDate");

            LocalDate startDate = LocalDate.parse(startDateStr);
            List<Sales> sales;

            sales = (endDateStr == null) ?
                    salesRepository.getSalesByDate(startDateStr) :
                    salesRepository.findByDateRange(startDate, LocalDate.parse(endDateStr));

            var customSales = createCustomSales(sales);

            var response = FilterSalesResponse.builder()
                    .success(true)
                    .message("Sales retrieved successfully")
                    .sales(customSales)
                    .build();

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withHeaders(HeadersUtil.getHeaders())
                    .withBody(mapper.writeValueAsString(response));

        } catch (Exception e) {
            return ResponseType.errorResponse(500, "Error retrieving sales: " + e.getMessage());
        }
    }

    private List<CustomSalesResponse> createCustomSales(List<Sales> sales) {
        return sales.stream()
                .map(productMapper::getCustomSales)
                .toList();
    }
}
