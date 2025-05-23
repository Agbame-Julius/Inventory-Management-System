package com.products.repository;

import com.products.model.Product;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

public class ProductRepository {
    private final DynamoDbTable<Product> productTable;

    public ProductRepository(DynamoDbEnhancedClient enhancedClient, String tableName) {
        productTable = enhancedClient.table(tableName, TableSchema.fromBean(Product.class));
    }

    public Product findByProductId(String productId) {
        return productTable.getItem(
                Key.builder()
                        .partitionValue(productId)
                        .build()
        );
    }

    public boolean existsByProductId(String productId) {
        return productTable.getItem(
                Key.builder()
                        .partitionValue(productId)
                        .build()
        ) != null;
    }


    public boolean existsByCategoryIdAndProductName(String categoryId, String productName) {
        QueryConditional query = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(categoryId).sortValue(productName).build()
        );

        return productTable.index("CategoryIndex")
                .query(query)
                .stream()
                .flatMap(page -> page.items().stream())
                .findAny()
                .isPresent();
    }

    public Product findByProductIdAndCategoryId(String productId, String categoryId) {
        var product = findByProductId(productId);
        return product.getCategoryId().equals(categoryId) ? product : null;
    }

    public void save(Product product) {
        productTable.putItem(product);
    }




}
