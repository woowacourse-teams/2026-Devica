package com.wrb.devica.probe.event;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class ExperimentEventService {

    private final ExperimentEventRepository experimentEventRepository;

    public ExperimentEventService(ExperimentEventRepository experimentEventRepository) {
        this.experimentEventRepository = experimentEventRepository;
    }

    @Transactional
    public void record(ExperimentEventRequest request) {
        experimentEventRepository.save(ExperimentEvent.from(request, Instant.now()));
    }
}
