package com.payu.mdr.dto;

import java.util.Map;

public record ApiErrorResponse(
        int status,
        String error,
        String message,
        String path,
        Map<String, String> fieldErrors
) {
}
