package com.wrb.devica.product;

import com.wrb.devica.common.BusinessErrorCode;
import com.wrb.devica.common.BusinessException;
import com.wrb.devica.purpose.UsagePurposeCode;
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

    public Slice<ProductSummaryResponse> findProductsByPurpose(String purposeCode, LaptopSearchCondition condition,
                                                               PageCondition pageCondition) {
        validatePurposeExists(purposeCode);

        return laptopRepository.findSummariesWithMinPriceForBE(
            condition,
            pageCondition.sort(),
            PageRequest.of(pageCondition.page(), pageCondition.size())
        );
    }

    public Product findProductById(Long productId) {
        return laptopRepository.findWithCpuById(productId)
            .orElseThrow(() -> new BusinessException(BusinessErrorCode.LAPTOP_NOT_FOUND));
    }

    private void validatePurposeExists(String purposeCode) {
        if (UsagePurposeCode.notExists(purposeCode)) {
            throw new BusinessException(BusinessErrorCode.USAGE_PURPOSE_NOT_FOUND);
        }
    }
}
