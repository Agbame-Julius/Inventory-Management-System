package com.products.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.products.response.SkippedProduct;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public  class ProductResponse {
    private int addedCount;
    private List<SkippedProduct> skippedProducts;
    private String errorMessage;

    public ProductResponse(int addedCount, List<SkippedProduct> skippedProducts) {
        this.addedCount = addedCount;
        this.skippedProducts = skippedProducts;
    }
}