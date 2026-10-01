package com.wrb.devica.purpose.repository;

import com.wrb.devica.category.domain.ProductCategoryCode;
import com.wrb.devica.purpose.domain.UsagePurpose;
import com.wrb.devica.purpose.domain.UsagePurposeCode;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsagePurposeRepository extends JpaRepository<UsagePurpose, Long> {

    Optional<UsagePurpose> findByProductCategory_CodeAndCode(ProductCategoryCode categoryCode,
                                                            UsagePurposeCode purposeCode);
}
