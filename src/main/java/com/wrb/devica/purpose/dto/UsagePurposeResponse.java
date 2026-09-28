package com.wrb.devica.purpose.dto;

import com.wrb.devica.purpose.domain.UsagePurposeCode;

public record UsagePurposeResponse(String code, String name) {

    public static UsagePurposeResponse from(UsagePurposeCode usagePurposeCode) {
        return new UsagePurposeResponse(usagePurposeCode.name(), usagePurposeCode.getDisplayName());
    }
}
