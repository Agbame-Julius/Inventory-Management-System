package com.products.request;

public record ResetPasswordRequest(
    String email,
    String newPassword,
    String session
) {
}
