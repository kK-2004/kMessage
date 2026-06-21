package com.kk2004.kmessage.domain;

public record DeliveryResult(Type type, String providerReference, String errorCode, String diagnostic) {
    public enum Type { SUCCESS, TRANSIENT_FAILURE, PERMANENT_FAILURE }

    public static DeliveryResult success(String reference) {
        return new DeliveryResult(Type.SUCCESS, reference, null, null);
    }

    public static DeliveryResult transientFailure(String code, String diagnostic) {
        return new DeliveryResult(Type.TRANSIENT_FAILURE, null, code, sanitize(diagnostic));
    }

    public static DeliveryResult permanentFailure(String code, String diagnostic) {
        return new DeliveryResult(Type.PERMANENT_FAILURE, null, code, sanitize(diagnostic));
    }

    private static String sanitize(String value) {
        if (value == null) return null;
        return value.substring(0, Math.min(value.length(), 512));
    }
}
