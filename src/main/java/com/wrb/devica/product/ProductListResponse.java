package com.wrb.devica.product;

import java.util.List;
import org.springframework.data.domain.Slice;

public record ProductListResponse(
        List<ProductSummaryResponse> content,
        int page,
        int size,
        boolean hasNext
) {
    public static ProductListResponse from(Slice<ProductSummaryResponse> productSlice) {
        return new ProductListResponse(
            productSlice.getContent(),
            productSlice.getPageable().getPageNumber(),
            productSlice.getSize(),
            productSlice.hasNext()
        );
    }
}
