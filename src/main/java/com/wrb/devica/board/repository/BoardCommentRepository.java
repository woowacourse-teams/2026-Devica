package com.wrb.devica.board.repository;

import com.wrb.devica.board.domain.BoardComment;
import com.wrb.devica.board.dto.BoardCommentResponse;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BoardCommentRepository extends JpaRepository<BoardComment, Long> {

    List<BoardCommentResponse> findByBoardPost_IdOrderByIdAsc(Long boardPostId);
}
