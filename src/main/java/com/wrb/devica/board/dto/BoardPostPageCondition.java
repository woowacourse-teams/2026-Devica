package com.wrb.devica.board.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.Objects;

public record BoardPostPageCondition(
    @PositiveOrZero(message = "페이지 번호는 0 이상이어야 합니다.")
    Integer page,
    @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
    @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.")
    Integer size
) {
    public BoardPostPageCondition {
        page = Objects.requireNonNullElse(page, 0);
        size = Objects.requireNonNullElse(size, 20);
    }

    // JPA는 조회 시작 위치를 int로 받는다. 범위를 벗어나면 조회 전에 요청 오류로 알린다.
    @AssertTrue(message = "조회 가능한 페이지 범위를 초과했습니다.")
    public boolean isOffsetInRange() {
        return (long) page * size <= Integer.MAX_VALUE;
    }
}
