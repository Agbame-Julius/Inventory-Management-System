package com.products.response;


import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomSalesResponse {
    private String salesId;
    private int quantity;
    private double totalPrice;
    private List<ProductDto> products;
    private LocalDate dateSold;
}
