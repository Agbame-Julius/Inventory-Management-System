package com.products.mapper;

import com.products.model.Product;
import com.products.model.Sales;
import com.products.repository.ProductRepository;
import com.products.response.CustomSalesResponse;
import com.products.response.ProductDto;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;

public class ProductMapper {
    private final ProductRepository productRepository;

    public ProductMapper(DynamoDbEnhancedClient enhancedClient) {
        String productTable = System.getenv("PRODUCT_TABLE");
        this.productRepository = new ProductRepository(enhancedClient, productTable);
    }

    public CustomSalesResponse getCustomSales(Sales sales) {

        return CustomSalesResponse.builder()
                .salesId(sales.getSalesId())
                .products(sales.getItems().stream()
                        .map(
                                item -> toProductDto(
                                        getProduct(item.getProductId())
                                )).toList())
                .quantity(sales.getQuantitySold())
                .totalPrice(sales.getTotalPrice())
                .dateSold(sales.getDateSold())
                .build();
    }

    private ProductDto toProductDto(Product product) {
        return ProductDto.builder()
                .productName(product.getProductName())
                .categoryName(product.getCategoryName())
                .sellingPrice(product.getTotalSellingPrice())
                .build();
    }

    private Product getProduct(String productId) {
        return productRepository.findByProductId(productId);
    }
}
