package com.products.model;

import lombok.*;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;
import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter;
import software.amazon.awssdk.enhanced.dynamodb.AttributeValueType;
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class Product {

    private String productId;
    private String productName;
    private double unitCostPrice;
    private int quantity;
    private LocalDate dateAdded;
    private LocalDate dateUpdated;
    private String categoryId;
    private String categoryName;
    private double totalPrice;
    private double unitSellingPrice;
    private double totalSellingPrice;

    @DynamoDbPartitionKey
    public String getProductId() {
        return productId;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = {"CategoryIndex"})
    public String getCategoryId() {
        return categoryId;
    }

    @DynamoDbSecondarySortKey(indexNames = {"CategoryIndex"})
    public String getProductName() {
        return productName;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = {"DateAddedIndex"})
    @DynamoDbConvertedBy(LocalDateAttributeConverter.class)
    public LocalDate getDateAdded() {
        return dateAdded;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = {"DateUpdatedIndex"})
    @DynamoDbConvertedBy(LocalDateAttributeConverter.class)
    public LocalDate getDateUpdated() {
        return dateUpdated;
    }

    public static class LocalDateAttributeConverter implements AttributeConverter<LocalDate> {
        private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

        @Override
        public AttributeValue transformFrom(LocalDate input) {
            return AttributeValue.builder().s(input.format(FORMATTER)).build();
        }

        @Override
        public LocalDate transformTo(AttributeValue input) {
            return LocalDate.parse(input.s(), FORMATTER);
        }

        @Override
        public EnhancedType<LocalDate> type() {
            return EnhancedType.of(LocalDate.class);
        }

        @Override
        public AttributeValueType attributeValueType() {
            return AttributeValueType.S;
        }
    }
}
