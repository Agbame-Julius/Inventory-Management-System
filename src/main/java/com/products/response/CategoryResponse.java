package com.products.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CategoryResponse {
    private int addedCount;
    private List<String> skippedCategories;
    private String errorMessage;

    public CategoryResponse(int addedCount, List<String> skippedCategories) {
        this.addedCount = addedCount;
        this.skippedCategories = skippedCategories;
    }
}
