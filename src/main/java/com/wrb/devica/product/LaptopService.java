package com.wrb.devica.product;

import com.wrb.devica.common.BusinessErrorCode;
import com.wrb.devica.common.BusinessException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
@RequiredArgsConstructor
@Service
public class LaptopService {

    private final LaptopRepository laptopRepository;
    private final ProductOfferRepository productOfferRepository;

    public Slice<LaptopSummaryResponse> findLaptops(LaptopSearchCondition condition, PageCondition pageCondition) {
        return laptopRepository.findSummariesWithMinPriceByCondition(
            condition,
            pageCondition.sort(),
            PageRequest.of(pageCondition.page(), pageCondition.size())
        );
    }

    public LaptopDetailResponse findLaptopById(Long productId) {
        Laptop laptop = laptopRepository.findById(productId)
            .orElseThrow(() -> new BusinessException(BusinessErrorCode.LAPTOP_NOT_FOUND));

        List<ProductOffer> offers = productOfferRepository
            .findAllByProductIdAndStatusOrderByPriceAsc(productId, OfferStatus.ON_SALE);

        return LaptopDetailResponse.of(laptop, offers);
    }

}
