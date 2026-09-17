package com.wrb.devica.product;

import com.wrb.devica.category.ProductCategoryCode;
import com.wrb.devica.common.BusinessErrorCode;
import com.wrb.devica.common.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
@RequiredArgsConstructor
@Service
public class ProductService {

    private final LaptopRepository laptopRepository;

    public Slice<ProductSummaryResponse> findProductsByCategory(String categoryCode, LaptopSearchCondition condition,
                                                                PageCondition pageCondition) {
        validateCategoryExists(categoryCode);

        return laptopRepository.findSummariesWithMinPrice(
            condition,
            pageCondition.sort(),
            PageRequest.of(pageCondition.page(), pageCondition.size())
        );
    }

    public Product findProductById(Long productId) {
        return laptopRepository.findWithCpuById(productId)
            .orElseThrow(() -> new BusinessException(BusinessErrorCode.LAPTOP_NOT_FOUND));
    }

    private void validateCategoryExists(String categoryCode) {
        ProductCategoryCode.from(categoryCode);
    }
}
