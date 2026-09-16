package com.wrb.devica.faq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wrb.devica.category.ProductCategory;
import com.wrb.devica.category.ProductCategoryCode;
import com.wrb.devica.category.ProductCategoryRepository;
import com.wrb.devica.common.BusinessErrorCode;
import com.wrb.devica.common.BusinessException;
import com.wrb.devica.common.JpaSliceTest;
import com.wrb.devica.purpose.UsagePurpose;
import com.wrb.devica.purpose.UsagePurposeCode;
import com.wrb.devica.purpose.UsagePurposeRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

@JpaSliceTest
@Import(FaqService.class)
class FaqServiceTest {

    private static final String LAPTOP = "LAPTOP";
    private static final String BACKEND_DEVELOPMENT = "BACKEND_DEVELOPMENT";

    @Autowired
    private FaqRepository faqRepository;

    @Autowired
    private ProductCategoryRepository productCategoryRepository;

    @Autowired
    private UsagePurposeRepository usagePurposeRepository;

    @Autowired
    private FaqService faqService;

    @Test
    void 공개된_홈페이지_FAQ를_노출_순서대로_반환한다() {
        // given
        faqRepository.save(Faq.home("second", "두 번째 질문", "답변", true, 2));
        faqRepository.save(Faq.home("first", "첫 번째 질문", "답변", true, 1));
        faqRepository.save(Faq.home("private", "비공개 질문", "답변", false, 0));
        faqRepository.save(Faq.forUsagePurpose(
            saveBackendDevelopmentPurpose(), "purpose", "목적별 질문", "답변", true, 0
        ));

        // when
        List<Faq> found = faqService.findPublishedHomeFaqs();

        // then
        assertThat(found).extracting(Faq::getSlug)
            .containsExactly("first", "second");
    }

    @Test
    void 공개_FAQ를_slug로_조회한다() {
        // given
        String slug = "how-to-use";
        faqRepository.save(Faq.home(slug, "첫 번째 질문", "답변", true, 1));

        // when
        Faq found = faqService.findPublishedFaqBySlug(slug);

        // then
        assertThat(found.getSlug()).isEqualTo(slug);
    }

    @Test
    void 존재하지_않는_slug로_조회하면_FAQ_NOT_FOUND가_발생한다() {
        // given
        String privateSlug = "private";
        faqRepository.save(Faq.home("how-to-use", "첫 번째 질문", "답변", true, 1));
        faqRepository.save(Faq.home(privateSlug, "비공개 질문", "답변", false, 0));

        // when & then
        assertThatThrownBy(() -> faqService.findPublishedFaqBySlug("UNKNOWN"))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(BusinessErrorCode.FAQ_NOT_FOUND);
    }

    @Test
    void 비공개_FAQ를_slug로_조회하면_FAQ_NOT_FOUND가_발생한다() {
        // given
        String privateSlug = "private";
        faqRepository.save(Faq.home("how-to-use", "첫 번째 질문", "답변", true, 1));
        faqRepository.save(Faq.home(privateSlug, "비공개 질문", "답변", false, 0));

        // when & then
        assertThatThrownBy(() -> faqService.findPublishedFaqBySlug(privateSlug))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(BusinessErrorCode.FAQ_NOT_FOUND);
    }

    @Test
    void 선택한_제품_종류와_사용_목적의_공개_FAQ를_노출_순서대로_반환한다() {
        // given
        UsagePurpose usagePurpose = saveBackendDevelopmentPurpose();
        faqRepository.save(Faq.forUsagePurpose(
            usagePurpose, "second", "두 번째 질문", "답변", true, 2
        ));
        faqRepository.save(Faq.forUsagePurpose(
            usagePurpose, "first", "첫 번째 질문", "답변", true, 1
        ));
        faqRepository.save(Faq.forUsagePurpose(
            usagePurpose, "private", "비공개 질문", "답변", false, 0
        ));
        faqRepository.save(Faq.home("home", "홈 질문", "답변", true, 0));

        // when
        List<Faq> found = faqService.findPublishedFaqsBy(LAPTOP, BACKEND_DEVELOPMENT);

        // then
        assertThat(found).extracting(Faq::getSlug)
            .containsExactly("first", "second");
    }

    @Test
    void 조건에_맞는_공개_FAQ가_없으면_빈_목록을_반환한다() {
        // when
        List<Faq> found = faqService.findPublishedFaqsBy(LAPTOP, BACKEND_DEVELOPMENT);

        // then
        assertThat(found).isEmpty();
    }

    private UsagePurpose saveBackendDevelopmentPurpose() {
        ProductCategory category = productCategoryRepository.save(
            ProductCategory.from(ProductCategoryCode.LAPTOP)
        );
        return usagePurposeRepository.save(
            UsagePurpose.of(category, UsagePurposeCode.BACKEND_DEVELOPMENT)
        );
    }

}
