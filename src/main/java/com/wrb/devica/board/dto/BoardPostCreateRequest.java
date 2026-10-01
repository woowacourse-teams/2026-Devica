package com.wrb.devica.board.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BoardPostCreateRequest(
    @NotBlank(message = "제목을 입력해 주세요.")
    @Size(max = 255, message = "제목은 255자 이하여야 합니다.")
    String title,
    @NotBlank(message = "본문을 입력해 주세요.")
    @Size(max = 10_000, message = "본문은 10,000자 이하여야 합니다.")
    String content
) {
}
