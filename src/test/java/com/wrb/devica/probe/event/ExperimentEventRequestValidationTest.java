package com.wrb.devica.probe.event;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.Instant;

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
                Instant.parse("2026-08-14T06:00:00Z")
        );

        assertThat(validator.validate(request)).isEmpty();
    }
}
