package com.wrb.devica.probe.event;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/probe/events")
public class ExperimentEventController {

    private final ExperimentEventService experimentEventService;

    public ExperimentEventController(ExperimentEventService experimentEventService) {
        this.experimentEventService = experimentEventService;
    }

    @PostMapping
    public ResponseEntity<Void> record(@Valid @RequestBody ExperimentEventRequest request) {
        experimentEventService.record(request);
        return ResponseEntity.accepted().build();
    }
}
