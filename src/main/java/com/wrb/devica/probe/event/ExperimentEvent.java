package com.wrb.devica.probe.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "experiment_event")
public class ExperimentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;

    @Column(name = "question_set_version", nullable = false, length = 64)
    private String questionSetVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_name", nullable = false, length = 48)
    private ExperimentEventName eventName;

    @Column(name = "question_id", length = 32)
    private String questionId;

    @Column(name = "option_id", length = 64)
    private String optionId;

    @Column(name = "recommendation_os", length = 32)
    private String recommendationOs;

    @Column(name = "recommendation_memory_gb")
    private Integer recommendationMemoryGb;

    @Column(name = "recommendation_storage_gb")
    private Integer recommendationStorageGb;

    @Column(name = "recommendation_cpu_tier", length = 64)
    private String recommendationCpuTier;

    @Column(name = "occurred_at", nullable = false, columnDefinition = "timestamp(6)")
    private Instant occurredAt;

    @Column(name = "received_at", nullable = false, columnDefinition = "timestamp(6)")
    private Instant receivedAt;

    protected ExperimentEvent() {
    }

    private ExperimentEvent(ExperimentEventRequest request, Instant receivedAt) {
        RecommendationSnapshotRequest snapshot = request.recommendationSnapshot();
        this.sessionId = request.sessionId();
        this.questionSetVersion = request.questionSetVersion();
        this.eventName = request.eventName();
        this.questionId = request.questionId();
        this.optionId = request.optionId();
        this.recommendationOs = snapshot == null ? null : snapshot.os();
        this.recommendationMemoryGb = snapshot == null ? null : snapshot.memoryGb();
        this.recommendationStorageGb = snapshot == null ? null : snapshot.storageGb();
        this.recommendationCpuTier = snapshot == null ? null : snapshot.cpuTier();
        this.occurredAt = request.occurredAt();
        this.receivedAt = receivedAt;
    }

    public static ExperimentEvent from(ExperimentEventRequest request, Instant receivedAt) {
        return new ExperimentEvent(request, receivedAt);
    }
}
