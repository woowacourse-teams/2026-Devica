package com.wrb.devica.faq.service;

import com.wrb.devica.category.domain.ProductCategoryCode;
import com.wrb.devica.common.exception.BusinessErrorCode;
import com.wrb.devica.common.exception.BusinessException;
import com.wrb.devica.faq.domain.Faq;
import com.wrb.devica.faq.repository.FaqRepository;
import com.wrb.devica.purpose.domain.UsagePurposeCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FaqService {

    private final FaqRepository faqRepository;

    public List<Faq> findPublishedServiceFaqs() {
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
