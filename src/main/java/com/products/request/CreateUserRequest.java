package com.products.request;

public record CreateUserRequest (
        String firstName,
        String lastName,
        String email
) {
}
