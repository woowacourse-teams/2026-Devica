package com.wrb.devica.product;

import java.util.List;
import org.springframework.data.domain.Slice;

public record LaptopListResponse(
        List<LaptopSummaryResponse> content,
        int page,
        int size,
        boolean hasNext
) {
    public static LaptopListResponse of(Slice<LaptopSummaryResponse> laptopSlice) {
        return new LaptopListResponse(
            laptopSlice.getContent(),
            laptopSlice.getPageable().getPageNumber(),
            laptopSlice.getSize(),
            laptopSlice.hasNext()
        );
    }
}
