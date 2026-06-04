package com.cafekiosk.global.rsData;

public record RsData<T>(
        String resultCode,
        String message,
        T data
) {
    public RsData(String resultCode, String message) {
        this(resultCode, message, null);
    }
}
