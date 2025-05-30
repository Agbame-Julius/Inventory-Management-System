package com.products.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ProductDto {
    private String productName;
    private String categoryName;
    private double sellingPrice;
}
