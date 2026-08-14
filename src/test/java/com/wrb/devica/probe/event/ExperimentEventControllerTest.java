package com.wrb.devica.probe.event;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ExperimentEventControllerTest {

    @Test
    void 이벤트를_저장하고_202를_반환한다() {
        ExperimentEventService service = mock(ExperimentEventService.class);
        ExperimentEventController controller = new ExperimentEventController(service);
        ExperimentEventRequest request = new ExperimentEventRequest(
                "session-id",
                "question-set-v0.1",
                ExperimentEventName.RECOMMENDATION_STARTED,
                null,
                null,
                null,
                Instant.parse("2026-08-14T06:00:00Z")
        );

        var response = controller.record(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        verify(service).record(request);
    }
}
