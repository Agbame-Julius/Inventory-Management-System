package com.products.request;

import java.util.List;

public record CreateSalesRequest(
        List<SaleLineItem> items
) {}
