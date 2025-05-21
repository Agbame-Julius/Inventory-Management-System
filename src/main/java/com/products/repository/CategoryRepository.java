package com.products.repository;

import com.products.model.Category;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Map;

public class CategoryRepository {
//
private final DynamoDbEnhancedClient enhancedClient;
    private final DynamoDbTable<Category> categoryTable;

    public CategoryRepository(DynamoDbEnhancedClient enhancedClient, String tableName) {
        this.enhancedClient = enhancedClient;
        this.categoryTable = enhancedClient.table(tableName, TableSchema.fromBean(Category.class));
    }

    public boolean existsByCategoryNameUsingScan(String categoryName) {
        return enhancedClient.table("YourTableName", TableSchema.fromBean(Category.class))
                .scan(r -> r.filterExpression(
                        Expression.builder()
                                .expression("categoryName = :name")
                                .expressionValues(Map.of(":name", AttributeValue.fromS(categoryName)))
                                .build())
                )
                .items().stream()
                .findFirst()
                .isPresent();
    }
}
