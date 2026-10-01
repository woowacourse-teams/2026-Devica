package com.wrb.devica.board.dto;

import java.util.List;
import org.springframework.data.domain.Slice;

public record BoardPostListResponse(
    List<BoardPostSummaryResponse> content,
    int page,
    int size,
    boolean hasNext
) {
    public static BoardPostListResponse from(Slice<BoardPostSummaryResponse> posts) {
        return new BoardPostListResponse(posts.getContent(), posts.getNumber(), posts.getSize(), posts.hasNext());
    }
}
