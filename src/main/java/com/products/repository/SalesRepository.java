package com.products.repository;

import com.products.model.Sales;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

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
}
