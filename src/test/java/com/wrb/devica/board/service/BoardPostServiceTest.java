package com.wrb.devica.board.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wrb.devica.board.domain.BoardPost;
import com.wrb.devica.board.dto.BoardPostCreateRequest;
import com.wrb.devica.board.dto.BoardPostDetailResponse;
import com.wrb.devica.board.dto.BoardPostPageCondition;
import com.wrb.devica.board.dto.BoardPostSummaryResponse;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Slice;

@JpaSliceTest
@Import(BoardPostService.class)
class BoardPostServiceTest {

    private static final String LAPTOP = "LAPTOP";
    private static final String BACKEND_DEVELOPMENT = "BACKEND_DEVELOPMENT";

    @Autowired
    private BoardPostService boardPostService;

    @Autowired
    private BoardPostRepository boardPostRepository;

    @Autowired
    private ProductCategoryRepository productCategoryRepository;

    @Autowired
    private UsagePurposeRepository usagePurposeRepository;

    private UsagePurpose usagePurpose;

    @BeforeEach
    void setUpPurpose() {
        ProductCategory category = productCategoryRepository.save(ProductCategory.from(ProductCategoryCode.LAPTOP));
        usagePurpose = usagePurposeRepository.save(UsagePurpose.of(category, UsagePurposeCode.BACKEND_DEVELOPMENT));
    }

    @Test
    void 글을_작성하면_선택한_사용_목적에_즉시_저장한다() {
        // given
        BoardPostCreateRequest request = new BoardPostCreateRequest("메모리 질문", "도커를 사용합니다.");

        // when
        Long id = boardPostService.create(LAPTOP, BACKEND_DEVELOPMENT, request);

        // then
        BoardPost saved = boardPostRepository.findById(id).orElseThrow();
        assertThat(saved.getUsagePurpose().getId()).isEqualTo(usagePurpose.getId());
        assertThat(saved.getTitle()).isEqualTo(request.title());
        assertThat(saved.getContent()).isEqualTo(request.content());
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @ParameterizedTest
    @CsvSource({
        "UNKNOWN, BACKEND_DEVELOPMENT, PRODUCT_CATEGORY_NOT_FOUND",
        "LAPTOP, UNKNOWN, USAGE_PURPOSE_NOT_FOUND"
    })
    void 존재하지_않는_제품이나_목적에_글을_작성하면_예외가_발생한다(
        String categoryCode, String purposeCode, BusinessErrorCode errorCode
    ) {
        BoardPostCreateRequest request = new BoardPostCreateRequest("제목", "본문");

        assertThatThrownBy(() -> boardPostService.create(categoryCode, purposeCode, request))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(errorCode);
        assertThat(boardPostRepository.count()).isZero();
    }

    @Test
    void DB에_사용_목적이_없으면_글을_작성할_수_없다() {
        usagePurposeRepository.deleteAll();
        BoardPostCreateRequest request = new BoardPostCreateRequest("제목", "본문");

        assertThatThrownBy(() -> boardPostService.create(LAPTOP, BACKEND_DEVELOPMENT, request))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(BusinessErrorCode.USAGE_PURPOSE_NOT_FOUND);
        assertThat(boardPostRepository.count()).isZero();
    }

    @Test
    void 선택한_목적의_글을_최신순으로_페이지_조회한다() {
        // given
        boardPostRepository.save(BoardPost.of(usagePurpose, "첫 번째", "본문"));
        boardPostRepository.save(BoardPost.of(usagePurpose, "두 번째", "본문"));
        boardPostRepository.save(BoardPost.of(usagePurpose, "세 번째", "본문"));

        // when
        Slice<BoardPostSummaryResponse> firstPage = boardPostService.findPosts(
            LAPTOP, BACKEND_DEVELOPMENT, new BoardPostPageCondition(0, 2)
        );
        Slice<BoardPostSummaryResponse> secondPage = boardPostService.findPosts(
            LAPTOP, BACKEND_DEVELOPMENT, new BoardPostPageCondition(1, 2)
        );

        // then
        assertThat(firstPage.getContent()).extracting(BoardPostSummaryResponse::title)
            .containsExactly("세 번째", "두 번째");
        assertThat(firstPage.hasNext()).isTrue();
        assertThat(secondPage.getContent()).extracting(BoardPostSummaryResponse::title)
            .containsExactly("첫 번째");
        assertThat(secondPage.hasNext()).isFalse();
    }

    @Test
    void 글이_없으면_빈_목록을_반환한다() {
        Slice<BoardPostSummaryResponse> found = boardPostService.findPosts(
            LAPTOP, BACKEND_DEVELOPMENT, new BoardPostPageCondition(null, null)
        );

        assertThat(found.getContent()).isEmpty();
        assertThat(found.hasNext()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
        "UNKNOWN, BACKEND_DEVELOPMENT, PRODUCT_CATEGORY_NOT_FOUND",
        "LAPTOP, UNKNOWN, USAGE_PURPOSE_NOT_FOUND"
    })
    void 존재하지_않는_제품이나_목적의_글_목록을_조회하면_예외가_발생한다(
        String categoryCode, String purposeCode, BusinessErrorCode errorCode
    ) {
        BoardPostPageCondition pageCondition = new BoardPostPageCondition(0, 20);

        assertThatThrownBy(() -> boardPostService.findPosts(categoryCode, purposeCode, pageCondition))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(errorCode);
    }

    @Test
    void DB에_사용_목적이_없으면_글_목록을_조회할_수_없다() {
        usagePurposeRepository.deleteAll();

        assertThatThrownBy(() -> boardPostService.findPosts(
            LAPTOP, BACKEND_DEVELOPMENT, new BoardPostPageCondition(0, 20)
        ))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(BusinessErrorCode.USAGE_PURPOSE_NOT_FOUND);
    }

    @Test
    void 게시글_ID로_본문과_제품_사용_목적을_조회한다() {
        // given
        BoardPost post = boardPostRepository.save(BoardPost.of(usagePurpose, "제목", "본문"));

        // when
        BoardPostDetailResponse found = boardPostService.findPost(post.getId());

        // then
        assertThat(found.id()).isEqualTo(post.getId());
        assertThat(found.title()).isEqualTo("제목");
        assertThat(found.content()).isEqualTo("본문");
        assertThat(found.categoryCode()).isEqualTo(LAPTOP);
        assertThat(found.purposeCode()).isEqualTo(BACKEND_DEVELOPMENT);
    }

    @Test
    void 존재하지_않는_ID로_게시글을_조회하면_예외가_발생한다() {
        assertThatThrownBy(() -> boardPostService.findPost(-1L))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(BusinessErrorCode.BOARD_POST_NOT_FOUND);
    }
}
