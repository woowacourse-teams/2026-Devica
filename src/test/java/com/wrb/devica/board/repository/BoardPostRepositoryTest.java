package com.wrb.devica.board.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.wrb.devica.board.domain.BoardPost;
import com.wrb.devica.board.dto.BoardPostSummaryResponse;
import com.wrb.devica.common.FlywayTestConfiguration;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("flyway-test")
@Import(FlywayTestConfiguration.class)
@Transactional
class BoardPostRepositoryTest {

    @Autowired
    private BoardPostRepository boardPostRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    void 선택한_목적의_게시글만_조회한다() {
        long purposeId = backendPurposeId();
        // 현재 운영 enum은 목적이 하나뿐이다. 마이그레이션 DB의 테스트용 행으로 목적 간 격리를 검증한다.
        jdbcTemplate.update("""
            INSERT INTO usage_purpose (product_category_id, code)
            SELECT product_category_id, 'BOARD_TEST_PURPOSE' FROM usage_purpose WHERE id = ?
            """, purposeId);
        long otherPurposeId = jdbcTemplate.queryForObject(
            "SELECT id FROM usage_purpose WHERE code = 'BOARD_TEST_PURPOSE'", Long.class
        );
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 1, 12, 0);
        insertPost(purposeId, "선택한 목적의 글", createdAt);
        insertPost(otherPurposeId, "다른 목적의 글", createdAt.plusHours(1));

        Slice<BoardPostSummaryResponse> found = boardPostRepository.findByUsagePurpose_IdOrderByCreatedAtDescIdDesc(
            purposeId, PageRequest.of(0, 20)
        );

        assertThat(found.getContent()).extracting(BoardPostSummaryResponse::title)
            .containsExactly("선택한 목적의 글");
        assertThat(found.hasNext()).isFalse();
    }

    @Test
    void 작성_시각을_우선하고_같은_시각이면_ID_내림차순으로_페이지를_나눈다() {
        long purposeId = backendPurposeId();
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 1, 12, 0);
        insertPost(purposeId, "최신 시각", createdAt.plusHours(1));
        insertPost(purposeId, "같은 시각 작은 ID", createdAt);
        insertPost(purposeId, "같은 시각 큰 ID", createdAt);

        Slice<BoardPostSummaryResponse> first = boardPostRepository.findByUsagePurpose_IdOrderByCreatedAtDescIdDesc(
            purposeId, PageRequest.of(0, 2)
        );
        Slice<BoardPostSummaryResponse> second = boardPostRepository.findByUsagePurpose_IdOrderByCreatedAtDescIdDesc(
            purposeId, PageRequest.of(1, 2)
        );

        assertThat(first.getContent()).extracting(BoardPostSummaryResponse::title)
            .containsExactly("최신 시각", "같은 시각 큰 ID");
        assertThat(first.hasNext()).isTrue();
        assertThat(second.getContent()).extracting(BoardPostSummaryResponse::title)
            .containsExactly("같은 시각 작은 ID");
        assertThat(second.hasNext()).isFalse();
    }

    @Test
    void 상세와_연관_정보를_함께_조회해_영속성_컨텍스트_밖에서도_읽을_수_있다() {
        long purposeId = backendPurposeId();
        insertPost(purposeId, "상세 조회", LocalDateTime.of(2026, 10, 1, 12, 0));
        long postId = jdbcTemplate.queryForObject("SELECT id FROM board_post WHERE title = '상세 조회'", Long.class);

        BoardPost found = boardPostRepository.findWithUsagePurposeById(postId).orElseThrow();
        entityManager.clear();

        assertThat(found.getContent()).isEqualTo("본문");
        assertThat(found.getUsagePurpose().getCode().name()).isEqualTo("BACKEND_DEVELOPMENT");
        assertThat(found.getUsagePurpose().getProductCategory().getCode().name()).isEqualTo("LAPTOP");
    }

    private long backendPurposeId() {
        return jdbcTemplate.queryForObject("""
            SELECT up.id FROM usage_purpose up
            JOIN product_category pc ON pc.id = up.product_category_id
            WHERE pc.code = 'LAPTOP' AND up.code = 'BACKEND_DEVELOPMENT'
            """, Long.class);
    }

    private void insertPost(long purposeId, String title, LocalDateTime createdAt) {
        jdbcTemplate.update("""
            INSERT INTO board_post (usage_purpose_id, title, content, created_at, updated_at)
            VALUES (?, ?, '본문', ?, ?)
            """, purposeId, title, createdAt, createdAt);
    }
}
