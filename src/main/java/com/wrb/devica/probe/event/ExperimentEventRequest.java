package com.wrb.devica.probe.event;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public record ExperimentEventRequest(
        @NotBlank @Size(max = 64) String sessionId,
        @NotBlank @Size(max = 64) String questionSetVersion,
        @NotNull ExperimentEventName eventName,
        @Size(max = 32) String questionId,
        @Size(max = 64) String optionId,
        @Valid RecommendationSnapshotRequest recommendationSnapshot,
        @Size(max = 2) List<@NotNull @Valid RecommendationSnapshotRequest> recommendationSnapshots,
        @NotNull Instant occurredAt
) {

    public List<RecommendationSnapshotRequest> normalizedRecommendationSnapshots() {
        if (recommendationSnapshots != null && !recommendationSnapshots.isEmpty()) {
            return List.copyOf(recommendationSnapshots);
        }
        if (recommendationSnapshot != null) {
            return List.of(recommendationSnapshot);
        }
        return List.of();
    }
}
