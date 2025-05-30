package com.products.model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.products.request.SaleLineItem;
import lombok.*;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;
import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter;
import software.amazon.awssdk.enhanced.dynamodb.AttributeValueType;
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@DynamoDbBean
public class Sales {

    private String salesId;
    private List<SaleLineItem> items;
    private int quantitySold;
    private double totalPrice;
    private LocalDate dateSold;
    private LocalDate dateUpdated;

    @DynamoDbPartitionKey
    public String getSalesId() {
        return salesId;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = {"DateSoldIndex"})
    @DynamoDbConvertedBy(LocalDateAttributeConverter.class)
    public LocalDate getDateSold() {
        return dateSold;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = {"DateUpdatedIndex"})
    @DynamoDbConvertedBy(LocalDateAttributeConverter.class)
    public LocalDate getDateUpdated() {
        return dateUpdated;
    }

    @DynamoDbConvertedBy(SaleLineItemListConverter.class)
    public List<SaleLineItem> getItems() {
        return items;
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

    public static class SaleLineItemListConverter implements AttributeConverter<List<SaleLineItem>> {
        private static final ObjectMapper MAPPER = new ObjectMapper();

        @Override
        public AttributeValue transformFrom(List<SaleLineItem> input) {
            try {
                return AttributeValue.builder().s(MAPPER.writeValueAsString(input)).build();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public List<SaleLineItem> transformTo(AttributeValue input) {
            try {
                return MAPPER.readValue(input.s(), new TypeReference<List<SaleLineItem>>() {});
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public EnhancedType<List<SaleLineItem>> type() {
            return EnhancedType.listOf(SaleLineItem.class);
        }

        @Override
        public AttributeValueType attributeValueType() {
            return AttributeValueType.S;
        }
    }
}
