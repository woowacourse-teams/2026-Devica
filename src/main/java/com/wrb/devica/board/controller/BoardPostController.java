package com.wrb.devica.board.controller;

import com.wrb.devica.board.dto.BoardPostCreateRequest;
import com.wrb.devica.board.dto.BoardPostCreateResponse;
import com.wrb.devica.board.dto.BoardPostDetailResponse;
import com.wrb.devica.board.dto.BoardPostListResponse;
import com.wrb.devica.board.dto.BoardPostPageCondition;
import com.wrb.devica.board.dto.BoardPostSummaryResponse;
import com.wrb.devica.board.service.BoardPostService;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Slice;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class BoardPostController {

    private final BoardPostService boardPostService;

    @PostMapping("/product-categories/{categoryCode}/usage-purposes/{purposeCode}/posts")
    public ResponseEntity<BoardPostCreateResponse> create(
        @PathVariable String categoryCode,
        @PathVariable String purposeCode,
        @Valid @RequestBody BoardPostCreateRequest request
    ) {
        Long id = boardPostService.create(categoryCode, purposeCode, request);
        URI boardPostUri = URI.create("/api/board-posts/" + id);
        BoardPostCreateResponse boardPostCreateResponse = new BoardPostCreateResponse(id);
        return ResponseEntity.created(boardPostUri).body(boardPostCreateResponse);
    }

    @GetMapping("/product-categories/{categoryCode}/usage-purposes/{purposeCode}/posts")
    public ResponseEntity<BoardPostListResponse> findPosts(
        @PathVariable String categoryCode,
        @PathVariable String purposeCode,
        @Valid @ModelAttribute BoardPostPageCondition pageCondition
    ) {
        Slice<BoardPostSummaryResponse> postSlice = boardPostService.findPosts(categoryCode, purposeCode,
            pageCondition);
        BoardPostListResponse boardPostListResponse = BoardPostListResponse.from(postSlice);
        return ResponseEntity.ok().body(boardPostListResponse);
    }

    @GetMapping("/board-posts/{id}")
    public ResponseEntity<BoardPostDetailResponse> findPost(@PathVariable Long id) {
        return ResponseEntity.ok().body(boardPostService.findPost(id));
    }
}
