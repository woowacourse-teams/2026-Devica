package com.wrb.devica.recommendation;

import com.wrb.devica.common.BusinessException;
import com.wrb.devica.common.ErrorCode;
import com.wrb.devica.product.SpecItem;
import com.wrb.devica.purpose.UsagePurposeCode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecommendationServiceTest {

    private final RecommendationService recommendationService = new RecommendationService();

    @ParameterizedTest
    @EnumSource(UsagePurposeCode.class)
    void 모든_사용_목적은_항목마다_근거를_갖춘_권장_사양을_반환한다(UsagePurposeCode purpose) {
        // when
        List<RecommendedSpec> recommendedSpecs = recommendationService.findByPurposeCode(purpose.name());

        // then
        assertThat(recommendedSpecs).isNotEmpty().allSatisfy(recommended -> {
            List<SpecItem> items = recommended.spec().toItems();
            assertThat(items).isNotEmpty();
            assertThat(items).allSatisfy(item ->
                    assertThat(recommended.itemReasons().get(item.code())).isNotBlank());
        });
    }

    @Test
    void 존재하지_않는_사용_목적_코드면_예외가_발생한다() {
        // when & then
        assertThatThrownBy(() -> recommendationService.findByPurposeCode("NOT_EXIST"))
                .isInstanceOf(BusinessException.class)
                .extracting(thrown -> ((BusinessException) thrown).getErrorCode())
                .isEqualTo(ErrorCode.USAGE_PURPOSE_NOT_FOUND);
    }
}
