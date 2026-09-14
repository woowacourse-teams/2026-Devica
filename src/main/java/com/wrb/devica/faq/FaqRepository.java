package com.wrb.devica.faq;

import com.wrb.devica.category.ProductCategoryCode;
import com.wrb.devica.purpose.UsagePurposeCode;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FaqRepository extends JpaRepository<Faq, Long> {

    List<Faq> findAllByUsagePurposeIsNullAndPublishedTrueOrderByDisplayOrderAsc();

    List<Faq> findAllByUsagePurpose_ProductCategory_CodeAndUsagePurpose_CodeAndPublishedTrueOrderByDisplayOrderAsc(
        ProductCategoryCode productCategoryCode,
        UsagePurposeCode usagePurposeCode
    );
}
