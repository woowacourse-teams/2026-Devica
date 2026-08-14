package com.wrb.devica.probe;

import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;

import static org.assertj.core.api.Assertions.assertThat;

class ProbePageControllerTest {

    @Test
    void 메인_화면에_질문_세트_승인_상태를_전달한다() {
        ProbePageController controller = new ProbePageController(new ProbePageService());
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.show(model);

        assertThat(view).isEqualTo("pages/probe/index");
        assertThat(model.get("page"))
                .isEqualTo(new ProbePageResponse("question-set-v0.1", true));
    }
}
