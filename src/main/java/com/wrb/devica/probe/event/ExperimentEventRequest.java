package com.wrb.devica.probe.event;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record ExperimentEventRequest(
        @NotBlank @Size(max = 64) String sessionId,
        @NotBlank @Size(max = 64) String questionSetVersion,
        @NotNull ExperimentEventName eventName,
        @Size(max = 32) String questionId,
        @Size(max = 64) String optionId,
        @Valid RecommendationSnapshotRequest recommendationSnapshot,
        @NotNull Instant occurredAt
) {
}
