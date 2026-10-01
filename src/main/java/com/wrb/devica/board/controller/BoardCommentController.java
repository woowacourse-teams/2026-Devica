package com.wrb.devica.board.controller;

import com.wrb.devica.board.dto.BoardCommentCreateRequest;
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

    @PostMapping
    public ResponseEntity<Void> create(
        @PathVariable Long postId,
        @Valid @RequestBody BoardCommentCreateRequest request
    ) {
        boardCommentService.create(postId, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping
    public ResponseEntity<List<BoardCommentResponse>> findComments(@PathVariable Long postId) {
        return ResponseEntity.ok().body(boardCommentService.findComments(postId));
    }
}
