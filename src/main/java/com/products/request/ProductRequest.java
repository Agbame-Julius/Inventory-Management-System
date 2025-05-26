package com.products.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductRequest {
    private List<ProductInput> products;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductInput {
        private String productName;
        private double unitCostPrice;
        private double unitSellingPrice;
        private int quantity;
        private String categoryId;
        private String categoryName;
    }
}
