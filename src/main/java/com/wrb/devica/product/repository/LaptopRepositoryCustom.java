package com.wrb.devica.product.repository;

import com.wrb.devica.product.dto.LaptopSearchCondition;
import com.wrb.devica.product.dto.ProductSummaryRow;
import com.wrb.devica.product.dto.SortType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

public interface LaptopRepositoryCustom {

    Slice<ProductSummaryRow> findSummariesWithMinPrice(LaptopSearchCondition condition, SortType sort,
                                                            Pageable pageable);
}
