package com.wrb.devica.probe.event;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ExperimentEventServiceTest {

    @Test
    void 수신_시각과_함께_이벤트를_저장한다() {
        ExperimentEventRepository repository = mock(ExperimentEventRepository.class);
        ExperimentEventService service = new ExperimentEventService(repository, new ObjectMapper());
        ExperimentEventRequest request = new ExperimentEventRequest(
                "session-id",
                "question-set-v0.1",
                ExperimentEventName.PRODUCT_LIST_VIEWED,
                null,
                null,
                null,
                null,
                Instant.parse("2026-08-14T06:00:00Z")
        );

        service.record(request);

        verify(repository).save(any(ExperimentEvent.class));
    }

    @Test
    void 두_OS_권장안을_JSON_한_건으로_저장하고_기존_단일_OS_컬럼은_비운다() {
        ExperimentEventRepository repository = mock(ExperimentEventRepository.class);
        ExperimentEventService service = new ExperimentEventService(repository, new ObjectMapper());
        ExperimentEventRequest request = new ExperimentEventRequest(
                "session-id",
                "question-set-v0.1",
                ExperimentEventName.SPEC_ADJUSTED,
                "Q1",
                "UNDECIDED",
                null,
                List.of(
                        new RecommendationSnapshotRequest("MACOS", 40, 512, "PRO"),
                        new RecommendationSnapshotRequest("WINDOWS", 48, 512, "H")
                ),
                Instant.parse("2026-08-14T06:00:00Z")
        );

        service.record(request);

        ArgumentCaptor<ExperimentEvent> captor = ArgumentCaptor.forClass(ExperimentEvent.class);
        verify(repository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getValue().recommendationOs()).isNull();
        assertThat(captor.getValue().recommendationSnapshotsJson())
                .contains("MACOS", "WINDOWS", "memoryGb");
    }

    @Test
    void 단수_권장안은_기존_컬럼과_JSON_컬럼에_함께_저장한다() {
        ExperimentEventRepository repository = mock(ExperimentEventRepository.class);
        ExperimentEventService service = new ExperimentEventService(repository, new ObjectMapper());
        ExperimentEventRequest request = new ExperimentEventRequest(
                "session-id",
                "question-set-v0.1",
                ExperimentEventName.SPEC_ADJUSTED,
                null,
                null,
                new RecommendationSnapshotRequest("MACOS", 24, 512, "BASIC"),
                null,
                Instant.parse("2026-08-14T06:00:00Z")
        );

        service.record(request);

        ArgumentCaptor<ExperimentEvent> captor = ArgumentCaptor.forClass(ExperimentEvent.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().recommendationOs()).isEqualTo("MACOS");
        assertThat(captor.getValue().recommendationSnapshotsJson()).contains("MACOS", "BASIC");
    }

    @Test
    void 단수와_복수가_함께_오면_복수를_우선해_저장한다() {
        ExperimentEventRepository repository = mock(ExperimentEventRepository.class);
        ExperimentEventService service = new ExperimentEventService(repository, new ObjectMapper());
        ExperimentEventRequest request = new ExperimentEventRequest(
                "session-id",
                "question-set-v0.1",
                ExperimentEventName.SPEC_ADJUSTED,
                null,
                null,
                new RecommendationSnapshotRequest("MACOS", 24, 512, "BASIC"),
                List.of(new RecommendationSnapshotRequest("WINDOWS", 32, 512, "P_HS")),
                Instant.parse("2026-08-14T06:00:00Z")
        );

        service.record(request);

        ArgumentCaptor<ExperimentEvent> captor = ArgumentCaptor.forClass(ExperimentEvent.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().recommendationOs()).isEqualTo("WINDOWS");
        assertThat(captor.getValue().recommendationSnapshotsJson())
                .contains("WINDOWS", "P_HS")
                .doesNotContain("MACOS", "BASIC");
    }
}
