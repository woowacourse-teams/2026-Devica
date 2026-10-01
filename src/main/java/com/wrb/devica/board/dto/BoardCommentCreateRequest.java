package com.wrb.devica.board.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BoardCommentCreateRequest(
    @NotBlank(message = "댓글을 입력해 주세요.")
    @Size(max = 1_000, message = "댓글은 1,000자 이하여야 합니다.")
    String content
) {
}
