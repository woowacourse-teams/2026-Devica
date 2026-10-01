package com.wrb.devica.board.service;

import com.wrb.devica.board.domain.BoardComment;
import com.wrb.devica.board.domain.BoardPost;
import com.wrb.devica.board.dto.BoardCommentCreateRequest;
import com.wrb.devica.board.dto.BoardCommentResponse;
import com.wrb.devica.board.repository.BoardCommentRepository;
import com.wrb.devica.board.repository.BoardPostRepository;
import com.wrb.devica.common.exception.BusinessErrorCode;
import com.wrb.devica.common.exception.BusinessException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BoardCommentService {

    private final BoardCommentRepository boardCommentRepository;
    private final BoardPostRepository boardPostRepository;

    @Transactional
    public Long create(Long postId, BoardCommentCreateRequest request) {
        BoardPost post = boardPostRepository.findById(postId)
            .orElseThrow(() -> new BusinessException(BusinessErrorCode.BOARD_POST_NOT_FOUND));
        BoardComment comment = boardCommentRepository.save(BoardComment.of(post, request.content()));
        return comment.getId();
    }

    public List<BoardCommentResponse> findComments(Long postId) {
        if (!boardPostRepository.existsById(postId)) {
            throw new BusinessException(BusinessErrorCode.BOARD_POST_NOT_FOUND);
        }
        return boardCommentRepository.findByBoardPost_IdOrderByIdAsc(postId);
    }
}
