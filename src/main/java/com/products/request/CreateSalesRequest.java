package com.products.request;

public record CreateSalesRequest (

  String productId,
  String categoryId,
  int quantitySold,
  double unitPrice,
  double totalPrice
) {
}
