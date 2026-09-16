package com.wrb.devica.faq;

import com.wrb.devica.category.ProductCategoryCode;
import com.wrb.devica.purpose.UsagePurposeCode;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FaqRepository extends JpaRepository<Faq, Long> {

    List<Faq> findAllByUsagePurposeIsNullAndPublishedTrueOrderByDisplayOrderAsc();

    Optional<Faq> findBySlugAndPublishedTrue(String slug);

    List<Faq> findAllByUsagePurpose_ProductCategory_CodeAndUsagePurpose_CodeAndPublishedTrueOrderByDisplayOrderAsc(
        ProductCategoryCode productCategoryCode,
        UsagePurposeCode usagePurposeCode
    );
}
