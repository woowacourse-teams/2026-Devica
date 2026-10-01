package com.wrb.devica.board.dto;

import java.time.LocalDateTime;

public record BoardCommentResponse(Long id, String content, LocalDateTime createdAt) {
}
