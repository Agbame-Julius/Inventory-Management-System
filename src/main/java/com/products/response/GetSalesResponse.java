package com.products.response;

import com.products.model.Sales;
import lombok.*;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class GetSalesResponse {
    private boolean success;
    private String message;
    private List<Sales> sales;
}
