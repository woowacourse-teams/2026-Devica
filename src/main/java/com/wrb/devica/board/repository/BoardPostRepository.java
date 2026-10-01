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
