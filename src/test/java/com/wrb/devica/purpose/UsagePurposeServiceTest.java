package com.wrb.devica.purpose;

import com.wrb.devica.common.BusinessException;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UsagePurposeServiceTest {

    private final UsagePurposeService usagePurposeService = new UsagePurposeService();

    @Test
    void 제품_종류에_속한_사용_목적을_반환한다() {
        // when
        List<UsagePurposeCode> purposes = usagePurposeService.findByCategoryCode("LAPTOP");

        // then
        assertThat(purposes).containsExactly(UsagePurposeCode.BACKEND_DEVELOPMENT);
    }

    @Test
    void 존재하지_않는_제품_종류_코드면_예외가_발생한다() {
        // when & then
        assertThatThrownBy(() -> usagePurposeService.findByCategoryCode("DESKTOP"))
                .isInstanceOf(BusinessException.class);
    }
}
