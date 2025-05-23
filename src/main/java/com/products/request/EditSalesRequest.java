package com.products.request;

import java.util.List;

public record EditSalesRequest(
        String salesId,
        List<SaleLineItem> items
) {}