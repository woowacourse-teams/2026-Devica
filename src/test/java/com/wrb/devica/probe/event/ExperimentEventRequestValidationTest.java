package com.wrb.devica.probe.event;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExperimentEventRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void 익명_세션과_버전과_이벤트_이름이_필수다() {
        ExperimentEventRequest request = new ExperimentEventRequest(
                "",
                "",
                null,
                null,
                null,
                null,
                null,
                Instant.parse("2026-08-14T06:00:00Z")
        );

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("sessionId", "questionSetVersion", "eventName");
    }

    @Test
    void 개인정보_없이_익명_행동_이벤트를_받는다() {
        ExperimentEventRequest request = new ExperimentEventRequest(
                "98800dbc-cba7-409c-89e2-60136d0946de",
                "question-set-v0.1",
                ExperimentEventName.QUESTION_ANSWERED,
                "Q1",
                "MAC",
                null,
                null,
                Instant.parse("2026-08-14T06:00:00Z")
        );

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void 권장_사양_스냅샷은_Mac과_Windows_두_개까지만_받는다() {
        RecommendationSnapshotRequest snapshot = new RecommendationSnapshotRequest("MACOS", 24, 512, "BASIC");
        ExperimentEventRequest request = new ExperimentEventRequest(
                "session-id",
                "question-set-v0.1",
                ExperimentEventName.SPEC_ADJUSTED,
                "Q1",
                "UNDECIDED",
                null,
                List.of(snapshot, snapshot, snapshot),
                Instant.parse("2026-08-14T06:00:00Z")
        );

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("recommendationSnapshots");
    }

    @Test
    void 단수_스냅샷을_한_개_목록으로_정규화한다() {
        RecommendationSnapshotRequest snapshot = new RecommendationSnapshotRequest("MACOS", 24, 512, "BASIC");
        ExperimentEventRequest request = new ExperimentEventRequest(
                "session-id",
                "question-set-v0.1",
                ExperimentEventName.SPEC_ADJUSTED,
                null,
                null,
                snapshot,
                null,
                Instant.parse("2026-08-14T06:00:00Z")
        );

        assertThat(request.normalizedRecommendationSnapshots()).containsExactly(snapshot);
    }

    @Test
    void 비어_있지_않은_복수_스냅샷이_단수보다_우선한다() {
        RecommendationSnapshotRequest singular = new RecommendationSnapshotRequest("MACOS", 24, 512, "BASIC");
        RecommendationSnapshotRequest plural = new RecommendationSnapshotRequest("WINDOWS", 32, 512, "P_HS");
        ExperimentEventRequest request = new ExperimentEventRequest(
                "session-id",
                "question-set-v0.1",
                ExperimentEventName.SPEC_ADJUSTED,
                null,
                null,
                singular,
                List.of(plural),
                Instant.parse("2026-08-14T06:00:00Z")
        );

        assertThat(request.normalizedRecommendationSnapshots()).containsExactly(plural);
    }

    @Test
    void 빈_복수_스냅샷은_단수_스냅샷으로_대체한다() {
        RecommendationSnapshotRequest singular = new RecommendationSnapshotRequest("MACOS", 24, 512, "BASIC");
        ExperimentEventRequest request = new ExperimentEventRequest(
                "session-id",
                "question-set-v0.1",
                ExperimentEventName.SPEC_ADJUSTED,
                null,
                null,
                singular,
                List.of(),
                Instant.parse("2026-08-14T06:00:00Z")
        );

        assertThat(request.normalizedRecommendationSnapshots()).containsExactly(singular);
    }

    @Test
    void 복수_스냅샷의_각_항목도_검증한다() {
        RecommendationSnapshotRequest invalid = new RecommendationSnapshotRequest("MACOS", -1, 512, "BASIC");
        ExperimentEventRequest request = new ExperimentEventRequest(
                "session-id",
                "question-set-v0.1",
                ExperimentEventName.SPEC_ADJUSTED,
                null,
                null,
                null,
                List.of(invalid),
                Instant.parse("2026-08-14T06:00:00Z")
        );

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("recommendationSnapshots[0].memoryGb");
    }
}
