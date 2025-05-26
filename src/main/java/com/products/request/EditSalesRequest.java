package com.products.request;

import java.util.List;

public record EditSalesRequest(List<SaleLineItem> items
) {}