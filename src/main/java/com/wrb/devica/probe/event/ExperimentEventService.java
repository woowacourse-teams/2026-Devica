package com.wrb.devica.probe.event;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;

@Service
public class ExperimentEventService {

    private final ExperimentEventRepository experimentEventRepository;
    private final ObjectMapper objectMapper;

    public ExperimentEventService(ExperimentEventRepository experimentEventRepository, ObjectMapper objectMapper) {
        this.experimentEventRepository = experimentEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void record(ExperimentEventRequest request) {
        experimentEventRepository.save(ExperimentEvent.from(request, serializeSnapshots(request), Instant.now()));
    }

    private String serializeSnapshots(ExperimentEventRequest request) {
        List<RecommendationSnapshotRequest> snapshots = request.normalizedRecommendationSnapshots();
        if (snapshots.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(snapshots);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("권장 사양 스냅샷을 저장할 수 없습니다.", exception);
        }
    }
}
