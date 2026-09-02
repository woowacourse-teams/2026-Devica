package com.wrb.devica.product;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class LaptopService {

    // 목록은 id 오름차순으로 고정한다. 정렬 선택지는 UC-09 에서 추가된다.
    private static final Sort DEFAULT_SORT = Sort.by("id");

    private final LaptopRepository laptopRepository;
    private final ProductOfferRepository productOfferRepository;

    public Slice<LaptopSummaryResponse> findLaptops(LaptopSearchCondition condition, int page, int size) {
        Slice<Laptop> laptops =
            laptopRepository.findAllByCondition(condition, PageRequest.of(page, size, DEFAULT_SORT));

        Map<Long, Long> minPrices = findMinPrices(laptops.getContent());
        return laptops.map(laptop -> LaptopSummaryResponse.from(laptop, minPrices.get(laptop.getId())));
    }

    // 조회한 노트북의 최저가를 한 번에 가져온다. 노트북마다 오퍼를 따라가면 그 수만큼 쿼리가 나간다.
    private Map<Long, Long> findMinPrices(List<Laptop> laptops) {
        List<Long> productIds = laptops.stream().map(Laptop::getId).toList();
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return productOfferRepository.findMinPrices(productIds, OfferStatus.ON_SALE).stream()
            .collect(Collectors.toMap(ProductMinPrice::productId, ProductMinPrice::minPrice));
    }
}
