package com.wrb.devica.product;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

public interface LaptopRepositoryCustom {

    Slice<ProductSummaryResponse> findSummariesWithMinPriceByCondition(LaptopSearchCondition condition, Pageable pageable);
}
