package com.wrb.devica.purpose;

public record UsagePurposeResponse(String code, String name) {

    public static UsagePurposeResponse from(UsagePurposeCode usagePurposeCode) {
        return new UsagePurposeResponse(usagePurposeCode.name(), usagePurposeCode.getDisplayName());
    }
}
