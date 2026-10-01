package com.wrb.devica.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class BusinessErrorCodeTest {

    @ParameterizedTest
    @EnumSource(BusinessErrorCode.class)
    void 비즈니스_예외는_4xx_상태만_쓴다(BusinessErrorCode errorCode) {
        // when & then
        assertThat(errorCode.getStatus().is4xxClientError()).isTrue();
    }
}
