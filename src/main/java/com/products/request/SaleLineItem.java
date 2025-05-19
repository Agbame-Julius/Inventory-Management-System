package com.products.request;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleLineItem {
    private String productId;
    private int quantitySold;
    private double unitPrice;
    private double totalPrice;
}
