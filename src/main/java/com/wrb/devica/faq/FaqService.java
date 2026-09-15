package com.wrb.devica.faq;

import com.wrb.devica.category.ProductCategoryCode;
import com.wrb.devica.common.BusinessErrorCode;
import com.wrb.devica.common.BusinessException;
import com.wrb.devica.purpose.UsagePurposeCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FaqService {

    private final FaqRepository faqRepository;

    public List<Faq> findPublishedHomeFaqs() {
        return faqRepository.findAllByUsagePurposeIsNullAndPublishedTrueOrderByDisplayOrderAsc();
    }

    public Faq findPublishedFaqBySlug(String slug) {
        return faqRepository.findBySlugAndPublishedTrue(slug)
            .orElseThrow(() -> new BusinessException(BusinessErrorCode.FAQ_NOT_FOUND));
    }

    public List<Faq> findPublishedFaqsBy(String categoryCode, String purposeCode) {
        ProductCategoryCode category = ProductCategoryCode.from(categoryCode);
        UsagePurposeCode purpose = UsagePurposeCode.from(purposeCode);
        return faqRepository.findAllByUsagePurpose_ProductCategory_CodeAndUsagePurpose_CodeAndPublishedTrueOrderByDisplayOrderAsc(
            category, purpose);
    }
}
