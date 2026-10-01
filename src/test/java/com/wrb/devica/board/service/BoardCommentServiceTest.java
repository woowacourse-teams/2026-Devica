package com.wrb.devica.board.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wrb.devica.board.domain.BoardComment;
import com.wrb.devica.board.domain.BoardPost;
import com.wrb.devica.board.dto.BoardCommentCreateRequest;
import com.wrb.devica.board.dto.BoardCommentResponse;
import com.wrb.devica.board.repository.BoardCommentRepository;
import com.wrb.devica.board.repository.BoardPostRepository;
import com.wrb.devica.category.domain.ProductCategory;
import com.wrb.devica.category.domain.ProductCategoryCode;
import com.wrb.devica.category.repository.ProductCategoryRepository;
import com.wrb.devica.common.JpaSliceTest;
import com.wrb.devica.common.exception.BusinessErrorCode;
import com.wrb.devica.common.exception.BusinessException;
import com.wrb.devica.purpose.domain.UsagePurpose;
import com.wrb.devica.purpose.domain.UsagePurposeCode;
import com.wrb.devica.purpose.repository.UsagePurposeRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

@JpaSliceTest
@Import(BoardCommentService.class)
class BoardCommentServiceTest {

    @Autowired
    private BoardCommentService boardCommentService;

    @Autowired
    private BoardCommentRepository boardCommentRepository;

    @Autowired
    private BoardPostRepository boardPostRepository;

    @Autowired
    private ProductCategoryRepository productCategoryRepository;

    @Autowired
    private UsagePurposeRepository usagePurposeRepository;

    private BoardPost post;

    @BeforeEach
    void setUpPost() {
        ProductCategory category = productCategoryRepository.save(ProductCategory.from(ProductCategoryCode.LAPTOP));
        UsagePurpose purpose = usagePurposeRepository.save(
            UsagePurpose.of(category, UsagePurposeCode.BACKEND_DEVELOPMENT));
        post = boardPostRepository.save(BoardPost.of(purpose, "메모리 질문", "도커를 사용합니다."));
    }

    @Test
    void 댓글을_작성하면_게시글에_즉시_저장한다() {
        // when
        Long id = boardCommentService.create(post.getId(),
            new BoardCommentCreateRequest("16GB면 충분합니다."));

        // then
        BoardComment saved = boardCommentRepository.findById(id).orElseThrow();
        assertThat(saved.getBoardPost().getId()).isEqualTo(post.getId());
        assertThat(saved.getContent()).isEqualTo("16GB면 충분합니다.");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void 존재하지_않는_게시글에_댓글을_작성하면_예외가_발생한다() {
        BoardCommentCreateRequest request = new BoardCommentCreateRequest("댓글");

        assertThatThrownBy(() -> boardCommentService.create(-1L, request))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(BusinessErrorCode.BOARD_POST_NOT_FOUND);
        assertThat(boardCommentRepository.count()).isZero();
    }

    @Test
    void 선택한_게시글의_댓글만_작성_순으로_조회한다() {
        // given
        BoardPost otherPost = boardPostRepository.save(BoardPost.of(post.getUsagePurpose(), "다른 글", "본문"));
        boardCommentRepository.save(BoardComment.of(post, "첫 번째"));
        boardCommentRepository.save(BoardComment.of(otherPost, "다른 글의 댓글"));
        boardCommentRepository.save(BoardComment.of(post, "두 번째"));

        // when
        List<BoardCommentResponse> found = boardCommentService.findComments(post.getId());

        // then
        assertThat(found).extracting(BoardCommentResponse::content)
            .containsExactly("첫 번째", "두 번째");
    }

    @Test
    void 댓글이_없으면_빈_목록을_반환한다() {
        assertThat(boardCommentService.findComments(post.getId())).isEmpty();
    }

    @Test
    void 존재하지_않는_게시글의_댓글을_조회하면_예외가_발생한다() {
        assertThatThrownBy(() -> boardCommentService.findComments(-1L))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(BusinessErrorCode.BOARD_POST_NOT_FOUND);
    }
}
