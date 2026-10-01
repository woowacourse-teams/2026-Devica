package com.wrb.devica.board.dto;

import java.time.LocalDateTime;

public record BoardPostSummaryResponse(Long id, String title, LocalDateTime createdAt) {
}
