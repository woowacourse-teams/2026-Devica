package com.wrb.devica.board.dto;

import com.wrb.devica.board.domain.BoardPost;
import com.wrb.devica.category.domain.ProductCategoryCode;
import com.wrb.devica.purpose.domain.UsagePurposeCode;
import java.time.LocalDateTime;

public record BoardPostDetailResponse(
    Long id,
    String title,
    String content,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    String categoryCode,
    String categoryName,
    String purposeCode,
    String purposeName
) {
    public static BoardPostDetailResponse from(BoardPost post) {
        ProductCategoryCode category = post.getUsagePurpose().getProductCategory().getCode();
        UsagePurposeCode purpose = post.getUsagePurpose().getCode();
        return new BoardPostDetailResponse(
            post.getId(), post.getTitle(), post.getContent(), post.getCreatedAt(), post.getUpdatedAt(),
            category.name(), category.getDisplayName(), purpose.name(), purpose.getDisplayName()
        );
    }
}
