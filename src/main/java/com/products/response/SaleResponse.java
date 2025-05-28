package com.products.response;

import com.products.model.Sales;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaleResponse {
    private boolean success;
    private String message;
    private Sales sale;
}
