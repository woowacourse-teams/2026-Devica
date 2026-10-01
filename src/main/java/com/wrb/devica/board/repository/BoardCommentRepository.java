package com.wrb.devica.board.repository;

import com.wrb.devica.board.domain.BoardComment;
import com.wrb.devica.board.dto.BoardCommentResponse;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BoardCommentRepository extends JpaRepository<BoardComment, Long> {

    // ID는 작성 순서대로 늘어나므로 작성 순 정렬로 쓴다. 게시글 FK 인덱스가 (board_post_id, id) 순서를 그대로 제공한다.
    List<BoardCommentResponse> findByBoardPost_IdOrderByIdAsc(Long boardPostId);
}
