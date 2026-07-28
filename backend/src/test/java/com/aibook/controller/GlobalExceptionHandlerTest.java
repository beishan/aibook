package com.aibook.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    @Test
    void badCredentialsReturnsUnauthorizedInsteadOfInternalServerError() {
        var response = new GlobalExceptionHandler().handleAuthentication(
                new BadCredentialsException("Bad credentials"));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("用户名或密码错误", response.getBody().get("message"));
    }
}
