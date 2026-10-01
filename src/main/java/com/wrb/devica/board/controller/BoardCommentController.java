package com.wrb.devica.board.controller;

import com.wrb.devica.board.dto.BoardCommentCreateRequest;
import com.wrb.devica.board.dto.BoardCommentCreateResponse;
import com.wrb.devica.board.dto.BoardCommentResponse;
import com.wrb.devica.board.service.BoardCommentService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/board-posts/{postId}/comments")
public class BoardCommentController {

    private final BoardCommentService boardCommentService;

    // 댓글은 따로 조회하는 주소가 없어 Location 헤더 없이 ID만 돌려준다.
    @PostMapping
    public ResponseEntity<BoardCommentCreateResponse> create(
        @PathVariable Long postId,
        @Valid @RequestBody BoardCommentCreateRequest request
    ) {
        Long id = boardCommentService.create(postId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(new BoardCommentCreateResponse(id));
    }

    // 게시글당 댓글이 많지 않다고 보고 페이지 없이 전부 돌려준다. 수백 개가 쌓이면 page·size를 추가한다.
    @GetMapping
    public ResponseEntity<List<BoardCommentResponse>> findComments(@PathVariable Long postId) {
        return ResponseEntity.ok().body(boardCommentService.findComments(postId));
    }
}
