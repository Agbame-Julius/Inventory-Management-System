package com.products.repository;

import com.products.model.Sales;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.LocalDate;
import java.util.ArrayList;
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

    public List<Sales> getSalesByDate(String date) {
        DynamoDbIndex<Sales> index = salesTable.index("DateSoldIndex");

        return index.query(QueryConditional.keyEqualTo(Key.builder()
                        .partitionValue(date)
                        .build()))
                .stream()
                .flatMap(page -> page.items().stream())
                .collect(Collectors.toList());
    }

    public List<Sales> findByDateRange(LocalDate startDate, LocalDate endDate) {
        List<Sales> allSales = new ArrayList<>();
        LocalDate currentDate = startDate;

        while (!currentDate.isAfter(endDate)) {
            allSales.addAll(getSalesByDate(currentDate.toString()));
            currentDate = currentDate.plusDays(1);
        }

        return allSales;
    }

    public PaginatedResult<Sales> findAllPaginated(int limit, String lastEvaluatedKey) {
        ScanEnhancedRequest.Builder requestBuilder = ScanEnhancedRequest.builder()
                .limit(limit);

        if (lastEvaluatedKey != null && !lastEvaluatedKey.trim().isEmpty()) {
            Map<String, AttributeValue> exclusiveStartKey = parseLastEvaluatedKey(lastEvaluatedKey);
            requestBuilder.exclusiveStartKey(exclusiveStartKey);
        }

        Page<Sales> page = salesTable.scan(requestBuilder.build()).iterator().next();

        List<Sales> items = page.items();
        String nextLastEvaluatedKey = null;

        if (page.lastEvaluatedKey() != null && !page.lastEvaluatedKey().isEmpty()) {
            nextLastEvaluatedKey = serializeLastEvaluatedKey(page.lastEvaluatedKey());
        }

        return new PaginatedResult<>(items, nextLastEvaluatedKey);
    }

    private Map<String, AttributeValue> parseLastEvaluatedKey(String lastEvaluatedKey) {

        return Map.of("salesId", AttributeValue.builder().s(lastEvaluatedKey).build());
    }

    private String serializeLastEvaluatedKey(Map<String, AttributeValue> lastEvaluatedKey) {
        AttributeValue salesIdValue = lastEvaluatedKey.get("salesId");
        if (salesIdValue != null && salesIdValue.s() != null) {
            return salesIdValue.s();
        }
        return null;
    }

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
