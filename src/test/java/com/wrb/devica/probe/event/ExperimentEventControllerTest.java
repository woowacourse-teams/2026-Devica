package com.wrb.devica.probe.event;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
                null,
                Instant.parse("2026-08-14T06:00:00Z")
        );

        var response = controller.record(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        verify(service).record(request);
    }

    @Test
    void 기존_단수_스냅샷_JSON을_받고_202를_반환한다() throws Exception {
        ExperimentEventService service = mock(ExperimentEventService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ExperimentEventController(service)).build();

        mockMvc.perform(post("/api/probe/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "session-id",
                                  "questionSetVersion": "question-set-v0.1",
                                  "eventName": "SPEC_ADJUSTED",
                                  "recommendationSnapshot": {
                                    "os": "MACOS",
                                    "memoryGb": 24,
                                    "storageGb": 512,
                                    "cpuTier": "BASIC"
                                  },
                                  "occurredAt": "2026-08-14T06:00:00Z"
                                }
                                """))
                .andExpect(status().isAccepted());

        verify(service).record(any(ExperimentEventRequest.class));
    }

    @Test
    void 복수_스냅샷_JSON을_받고_202를_반환한다() throws Exception {
        ExperimentEventService service = mock(ExperimentEventService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ExperimentEventController(service)).build();

        mockMvc.perform(post("/api/probe/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "session-id",
                                  "questionSetVersion": "question-set-v0.1",
                                  "eventName": "SPEC_ADJUSTED",
                                  "recommendationSnapshots": [
                                    {"os": "MACOS", "memoryGb": 24, "storageGb": 512, "cpuTier": "BASIC"},
                                    {"os": "WINDOWS", "memoryGb": 32, "storageGb": 512, "cpuTier": "P_HS"}
                                  ],
                                  "occurredAt": "2026-08-14T06:00:00Z"
                                }
                                """))
                .andExpect(status().isAccepted());

        verify(service).record(any(ExperimentEventRequest.class));
    }

    @Test
    void 복수_스냅샷이_세_개면_400을_반환한다() throws Exception {
        ExperimentEventService service = mock(ExperimentEventService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ExperimentEventController(service)).build();

        mockMvc.perform(post("/api/probe/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "session-id",
                                  "questionSetVersion": "question-set-v0.1",
                                  "eventName": "SPEC_ADJUSTED",
                                  "recommendationSnapshots": [
                                    {"os": "MACOS", "memoryGb": 24, "storageGb": 512, "cpuTier": "BASIC"},
                                    {"os": "WINDOWS", "memoryGb": 32, "storageGb": 512, "cpuTier": "P_HS"},
                                    {"os": "WINDOWS", "memoryGb": 32, "storageGb": 512, "cpuTier": "H"}
                                  ],
                                  "occurredAt": "2026-08-14T06:00:00Z"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 잘못된_스냅샷이면_400을_반환한다() throws Exception {
        ExperimentEventService service = mock(ExperimentEventService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ExperimentEventController(service)).build();

        mockMvc.perform(post("/api/probe/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "session-id",
                                  "questionSetVersion": "question-set-v0.1",
                                  "eventName": "SPEC_ADJUSTED",
                                  "recommendationSnapshots": [
                                    {"os": "MACOS", "memoryGb": -1, "storageGb": 512, "cpuTier": "BASIC"}
                                  ],
                                  "occurredAt": "2026-08-14T06:00:00Z"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }
}
