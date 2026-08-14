package com.wrb.devica.probe;

public record ProbePageResponse(
        String questionSetVersion,
        boolean questionSetApproved
) {
}
