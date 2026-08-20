package com.wrb.devica.guide;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GuidePageControllerTest {

    @Test
    void 가이드_초기_화면을_반환한다() {
        GuidePageController controller = new GuidePageController();

        String view = controller.show();

        assertThat(view).isEqualTo("pages/guide/index");
    }

    @Test
    void 권장_사양_근거_상세_화면을_반환한다() {
        GuidePageController controller = new GuidePageController();

        String view = controller.showWhy24Gb512Gb();

        assertThat(view).isEqualTo("pages/guide/why-24gb-512gb");
    }

    @Test
    void 노트북_선택_기준_상세_화면을_반환한다() {
        GuidePageController controller = new GuidePageController();

        String view = controller.showWhatToConsider();

        assertThat(view).isEqualTo("pages/guide/what-to-consider");
    }

    @Test
    void 사용자_상황별_조정_상세_화면을_반환한다() {
        GuidePageController controller = new GuidePageController();

        String view = controller.showAdjustForYourNeeds();

        assertThat(view).isEqualTo("pages/guide/adjust-for-your-needs");
    }
}
