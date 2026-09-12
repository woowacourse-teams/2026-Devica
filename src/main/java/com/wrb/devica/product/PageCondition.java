package com.wrb.devica.product;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.Objects;

public record PageCondition(
        @PositiveOrZero(message = "페이지 번호는 0 이상이어야 합니다.")
        Integer page,
        @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
        @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.")
        Integer size,
        SortType sort
) {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    public PageCondition {
        page = Objects.requireNonNullElse(page, DEFAULT_PAGE);
        size = Objects.requireNonNullElse(size, DEFAULT_SIZE);
    }
}
