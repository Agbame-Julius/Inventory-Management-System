package com.products.sales;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.products.repository.SalesRepository;
import com.products.response.ResponseType;
import com.products.response.SaleResponse;
import com.products.utils.CognitoUtil;
import com.products.utils.HeadersUtil;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public class GetSaleHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

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

            if(!CognitoUtil.isSalesPerson(event)){
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(401)
                        .withBody("User not authorized to perform this operation");
            }

            var salesId = event.getPathParameters().get("salesId");

            if (salesId == null || salesId.isEmpty())
                return ResponseType.errorResponse(400, "Sales ID is required");

            var sale = salesRepository.findBySalesId(salesId);
            if (sale == null)
                return ResponseType.errorResponse(404, "Sale not found");

            return  new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withHeaders(HeadersUtil.getHeaders())
                    .withBody(mapper.writeValueAsString(
                            SaleResponse.builder()
                                    .success(true)
                                    .message("Sale retrieved successfully")
                                    .sale(sale)
                                    .build()
                    ));

        } catch (Exception e) {
            return ResponseType.errorResponse(500, "Error retrieving sale: " + e.getMessage());
        }

    }
}
