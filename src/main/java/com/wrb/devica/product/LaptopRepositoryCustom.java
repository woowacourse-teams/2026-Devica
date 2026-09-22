package com.wrb.devica.product;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

public interface LaptopRepositoryCustom {

    Slice<ProductSummaryResponse> findSummariesWithMinPrice(LaptopSearchCondition condition, SortType sort,
                                                            Pageable pageable);
}
