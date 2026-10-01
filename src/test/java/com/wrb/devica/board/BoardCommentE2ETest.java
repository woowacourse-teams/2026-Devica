package com.wrb.devica.board;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import com.wrb.devica.board.domain.BoardPost;
import com.wrb.devica.board.dto.BoardCommentCreateRequest;
import com.wrb.devica.board.repository.BoardPostRepository;
import com.wrb.devica.category.domain.ProductCategory;
import com.wrb.devica.category.domain.ProductCategoryCode;
import com.wrb.devica.category.repository.ProductCategoryRepository;
import com.wrb.devica.common.E2ETest;
import com.wrb.devica.purpose.domain.UsagePurpose;
import com.wrb.devica.purpose.domain.UsagePurposeCode;
import com.wrb.devica.purpose.repository.UsagePurposeRepository;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

class BoardCommentE2ETest extends E2ETest {

    @Autowired
    private ProductCategoryRepository productCategoryRepository;

    @Autowired
    private UsagePurposeRepository usagePurposeRepository;

    @Autowired
    private BoardPostRepository boardPostRepository;

    private String commentsPath;

    @BeforeEach
    void setUpPost() {
        ProductCategory category = productCategoryRepository.save(ProductCategory.from(ProductCategoryCode.LAPTOP));
        UsagePurpose purpose = usagePurposeRepository.save(UsagePurpose.of(category, UsagePurposeCode.BACKEND_DEVELOPMENT));
        BoardPost post = boardPostRepository.save(BoardPost.of(purpose, "메모리 질문", "도커를 사용합니다."));
        commentsPath = "/api/board-posts/" + post.getId() + "/comments";
    }

    @Test
    void 게시글에_댓글을_작성하면_댓글_목록에서_즉시_조회할_수_있다() {
        // when: 로그인 없이 댓글을 작성한다.
        given()
            .contentType(ContentType.JSON)
            .body(new BoardCommentCreateRequest("16GB면 충분합니다."))
            .when().post(commentsPath)
            .then().statusCode(201);

        // then
        given()
            .when().get(commentsPath)
            .then().statusCode(200)
            .body("[0].id", notNullValue())
            .body("content", contains("16GB면 충분합니다."))
            .body("[0].createdAt", notNullValue());
    }

    @Test
    void 댓글은_1000자까지_작성할_수_있다() {
        given()
            .contentType(ContentType.JSON)
            .body(new BoardCommentCreateRequest("가".repeat(1_000)))
            .when().post(commentsPath)
            .then().statusCode(201);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1_001})
    void 비었거나_1000자를_넘는_댓글은_작성할_수_없다(int length) {
        given()
            .contentType(ContentType.JSON)
            .body(new BoardCommentCreateRequest("가".repeat(length)))
            .when().post(commentsPath)
            .then().statusCode(400)
            .body("code", is("INVALID_REQUEST"));
    }

    @Test
    void 공백만_입력한_댓글은_작성할_수_없다() {
        given()
            .contentType(ContentType.JSON)
            .body(new BoardCommentCreateRequest("   "))
            .when().post(commentsPath)
            .then().statusCode(400)
            .body("code", is("INVALID_REQUEST"));
    }

    @Test
    void 존재하지_않는_게시글의_댓글은_작성하거나_조회할_수_없다() {
        String missingPath = "/api/board-posts/-1/comments";

        given()
            .contentType(ContentType.JSON)
            .body(new BoardCommentCreateRequest("댓글"))
            .when().post(missingPath)
            .then().statusCode(404)
            .body("code", is("BOARD_POST_NOT_FOUND"));
        given()
            .when().get(missingPath)
            .then().statusCode(404)
            .body("code", is("BOARD_POST_NOT_FOUND"));
    }
}
