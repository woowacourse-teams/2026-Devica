package com.wrb.devica.product;

import com.wrb.devica.common.BusinessErrorCode;
import com.wrb.devica.common.BusinessException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class LaptopService {

    // TODO: 목록은 id 오름차순으로 고정한다. 정렬 선택지는 UC-09 에서 추가된다.
    private static final Sort DEFAULT_SORT = Sort.by("id");

    private final LaptopRepository laptopRepository;
    private final ProductOfferRepository productOfferRepository;

    public Slice<LaptopSummaryResponse> findLaptops(LaptopSearchCondition condition, int page, int size) {
        return laptopRepository.findAllByCondition(
            condition,
            PageRequest.of(page, size, DEFAULT_SORT)
        );
    }

    @Transactional(readOnly = true)
    public LaptopDetailResponse findLaptopById(Long id) {
        Laptop laptop = laptopRepository.findById(id)
            .orElseThrow(() -> new BusinessException(BusinessErrorCode.LAPTOP_NOT_FOUND));

        List<ProductOffer> offers = productOfferRepository
            .findAllByProductIdAndStatusOrderByPriceAsc(id, OfferStatus.ON_SALE);

        if (offers.isEmpty()) {
            throw new BusinessException(BusinessErrorCode.LAPTOP_NOT_FOUND);
        }

        return LaptopDetailResponse.of(laptop, offers);
    }

}
