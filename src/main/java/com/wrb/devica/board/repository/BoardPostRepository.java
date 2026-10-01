package com.wrb.devica.board.repository;

import com.wrb.devica.board.domain.BoardPost;
import com.wrb.devica.board.dto.BoardPostSummaryResponse;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BoardPostRepository extends JpaRepository<BoardPost, Long> {

    Slice<BoardPostSummaryResponse> findByUsagePurpose_IdOrderByCreatedAtDescIdDesc(
        Long usagePurposeId, Pageable pageable
    );

    @EntityGraph(attributePaths = {"usagePurpose", "usagePurpose.productCategory"})
    Optional<BoardPost> findWithUsagePurposeById(Long id);
}
