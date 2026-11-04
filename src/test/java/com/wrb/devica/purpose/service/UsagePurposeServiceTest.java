package com.wrb.devica.purpose.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wrb.devica.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

class UsagePurposeServiceTest {

    private final UsagePurposeService usagePurposeService = new UsagePurposeService();

    @Test
    void 존재하지_않는_제품_종류_코드면_예외가_발생한다() {
        // when & then
        assertThatThrownBy(() -> usagePurposeService.findByCategoryCode("DESKTOP"))
            .isInstanceOf(BusinessException.class);
    }
}
