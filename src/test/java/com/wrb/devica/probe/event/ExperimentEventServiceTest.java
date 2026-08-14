package com.wrb.devica.probe.event;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ExperimentEventServiceTest {

    @Test
    void 수신_시각과_함께_이벤트를_저장한다() {
        ExperimentEventRepository repository = mock(ExperimentEventRepository.class);
        ExperimentEventService service = new ExperimentEventService(repository);
        ExperimentEventRequest request = new ExperimentEventRequest(
                "session-id",
                "question-set-v0.1",
                ExperimentEventName.PRODUCT_LIST_VIEWED,
                null,
                null,
                null,
                Instant.parse("2026-08-14T06:00:00Z")
        );

        service.record(request);

        verify(repository).save(any(ExperimentEvent.class));
    }
}
