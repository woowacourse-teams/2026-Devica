package com.wrb.devica.product.service;

import com.wrb.devica.category.domain.ProductCategoryCode;
import com.wrb.devica.common.exception.BusinessErrorCode;
import com.wrb.devica.common.exception.BusinessException;
import com.wrb.devica.product.domain.Product;
import com.wrb.devica.product.dto.LaptopSearchCondition;
import com.wrb.devica.product.dto.PageCondition;
import com.wrb.devica.product.dto.ProductSummaryResponse;
import com.wrb.devica.product.repository.LaptopRepository;
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
