package com.wrb.devica.product;

import com.wrb.devica.common.BusinessErrorCode;
import com.wrb.devica.common.BusinessException;
import com.wrb.devica.purpose.UsagePurposeCode;
import java.util.List;
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
    private final ProductOfferRepository productOfferRepository;

    public Slice<ProductSummaryResponse> findProducts(String purposeCode, LaptopSearchCondition condition,
                                                      PageCondition pageCondition) {
        validatePurposeExists(purposeCode);

        return laptopRepository.findSummariesWithMinPriceByCondition(
            condition,
            pageCondition.sort(),
            PageRequest.of(pageCondition.page(), pageCondition.size())
        );
    }

    public ProductDetailResponse findProductById(Long productId) {
        Laptop laptop = laptopRepository.findById(productId)
            .orElseThrow(() -> new BusinessException(BusinessErrorCode.LAPTOP_NOT_FOUND));

        List<ProductOffer> offers = productOfferRepository
            .findAllByProductIdAndStatusOrderByPriceAsc(productId, OfferStatus.ON_SALE);

        return ProductDetailResponse.of(laptop, offers);
    }

    private void validatePurposeExists(String purposeCode) {
        if (UsagePurposeCode.notExists(purposeCode)) {
            throw new BusinessException(BusinessErrorCode.USAGE_PURPOSE_NOT_FOUND);
        }
    }
}
