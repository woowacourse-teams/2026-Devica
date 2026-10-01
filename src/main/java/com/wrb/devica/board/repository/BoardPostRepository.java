package com.wrb.devica.board.repository;

import com.wrb.devica.board.domain.BoardPost;
import com.wrb.devica.board.dto.BoardPostSummaryResponse;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BoardPostRepository extends JpaRepository<BoardPost, Long> {

    // 댓글 수는 컬럼으로 두지 않고 조회할 때 센다. 게시글 FK 인덱스로 글마다 댓글 행만 읽는다.
    @Query("""
        SELECT new com.wrb.devica.board.dto.BoardPostSummaryResponse(
            p.id, p.title, p.createdAt,
            (SELECT COUNT(c) FROM BoardComment c WHERE c.boardPost = p)
        )
        FROM BoardPost p
        WHERE p.usagePurpose.id = :usagePurposeId
        ORDER BY p.createdAt DESC, p.id DESC
        """)
    Slice<BoardPostSummaryResponse> findSummariesByUsagePurposeId(Long usagePurposeId, Pageable pageable);

    @EntityGraph(attributePaths = {"usagePurpose", "usagePurpose.productCategory"})
    Optional<BoardPost> findWithUsagePurposeById(Long id);
}
