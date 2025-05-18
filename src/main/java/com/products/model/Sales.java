package com.products.model;

import lombok.*;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;
import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter;
import software.amazon.awssdk.enhanced.dynamodb.AttributeValueType;
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@DynamoDbBean
public class Sales {

    private String salesId;
    private String productId;
    private String categoryId;
    private int quantitySold;
    private double unitPrice;
    private double totalPrice;
    private LocalDate dateSold;
    private LocalDate dateUpdated;

    @DynamoDbPartitionKey
    public String getSalesId() {
        return salesId;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = {"ProductIndex"})
    public String getProductId() {
        return productId;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = {"CategoryIndex"})
    public String getCategoryId() {
        return categoryId;
    }

    @DynamoDbSecondarySortKey(indexNames = {"ProductIndex", "CategoryIndex"})
    @DynamoDbConvertedBy(LocalDateAttributeConverter.class)
    public LocalDate getDateSold() {
        return dateSold;
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
