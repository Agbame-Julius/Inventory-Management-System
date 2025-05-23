package com.products.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SkippedProduct {
    private String productName;
    private String categoryId;
    private String reason;
}

