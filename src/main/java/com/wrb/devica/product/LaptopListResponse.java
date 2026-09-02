package com.wrb.devica.product;

import java.util.List;

public record LaptopListResponse(
        List<LaptopSummaryResponse> content,
        int page,
        int size,
        boolean hasNext
) {
}
