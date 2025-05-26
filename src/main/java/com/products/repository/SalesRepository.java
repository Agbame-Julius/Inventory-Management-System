package com.products.repository;

import com.products.model.Sales;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SalesRepository {
    private final DynamoDbTable<Sales> salesTable;

    public SalesRepository(DynamoDbEnhancedClient enhancedClient, String tableName) {
        salesTable = enhancedClient.table(tableName, TableSchema.fromBean(Sales.class));
    }

    public void save(Sales sales) {
        salesTable.putItem(sales);
    }

    public Sales findBySalesId(String salesId) {
        return salesTable.getItem(
                Key.builder()
                        .partitionValue(salesId)
                        .build()
        );
    }

    public List<Sales> findAll() {
        return salesTable.scan().items().stream().collect(Collectors.toList());
    }

    public PaginatedResult<Sales> findAllPaginated(int limit, String lastEvaluatedKey) {
        ScanEnhancedRequest.Builder requestBuilder = ScanEnhancedRequest.builder()
                .limit(limit);

        // If lastEvaluatedKey is provided, set it as the exclusive start key
        if (lastEvaluatedKey != null && !lastEvaluatedKey.trim().isEmpty()) {
            Map<String, AttributeValue> exclusiveStartKey = parseLastEvaluatedKey(lastEvaluatedKey);
            requestBuilder.exclusiveStartKey(exclusiveStartKey);
        }

        Page<Sales> page = salesTable.scan(requestBuilder.build()).iterator().next();

        List<Sales> items = page.items();
        String nextLastEvaluatedKey = null;

        // Convert lastEvaluatedKey back to string format for the response
        if (page.lastEvaluatedKey() != null && !page.lastEvaluatedKey().isEmpty()) {
            nextLastEvaluatedKey = serializeLastEvaluatedKey(page.lastEvaluatedKey());
        }

        return new PaginatedResult<>(items, nextLastEvaluatedKey);
    }

    private Map<String, AttributeValue> parseLastEvaluatedKey(String lastEvaluatedKey) {
        // For simplicity, assuming the partition key is the sales ID
        // In a real scenario, you might want to use Base64 encoding/decoding
        // or a more sophisticated serialization method
        return Map.of("salesId", AttributeValue.builder().s(lastEvaluatedKey).build());
    }

    private String serializeLastEvaluatedKey(Map<String, AttributeValue> lastEvaluatedKey) {
        // Extract the partition key value (salesId in this case)
        AttributeValue salesIdValue = lastEvaluatedKey.get("salesId");
        if (salesIdValue != null && salesIdValue.s() != null) {
            return salesIdValue.s();
        }
        return null;
    }

    // Inner class to hold paginated results
    public static class PaginatedResult<T> {
        private final List<T> items;
        private final String lastEvaluatedKey;

        public PaginatedResult(List<T> items, String lastEvaluatedKey) {
            this.items = items;
            this.lastEvaluatedKey = lastEvaluatedKey;
        }

        public List<T> getItems() {
            return items;
        }

        public String getLastEvaluatedKey() {
            return lastEvaluatedKey;
        }
    }

}
