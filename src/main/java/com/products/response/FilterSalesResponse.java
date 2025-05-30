package com.products.response;

import lombok.*;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class FilterSalesResponse {
    private boolean success;
    private String message;
    private List<CustomSalesResponse> sales;
}
