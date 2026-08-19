package com.wrb.devica.probe.event;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record RecommendationSnapshotRequest(
        @Size(max = 32) String os,
        @Min(0) @Max(1024) Integer memoryGb,
        @Min(0) @Max(16384) Integer storageGb,
        @Size(max = 64) String cpuTier
) {
}
