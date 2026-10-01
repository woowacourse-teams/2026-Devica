package com.wrb.devica.board;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import com.wrb.devica.board.dto.BoardPostCreateRequest;
import com.wrb.devica.category.domain.ProductCategory;
import com.wrb.devica.category.domain.ProductCategoryCode;
import com.wrb.devica.category.repository.ProductCategoryRepository;
import com.wrb.devica.common.E2ETest;
import com.wrb.devica.purpose.domain.UsagePurpose;
import com.wrb.devica.purpose.domain.UsagePurposeCode;
import com.wrb.devica.purpose.repository.UsagePurposeRepository;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class BoardPostE2ETest extends E2ETest {

    private static final String BOARD_PATH =
        "/api/product-categories/LAPTOP/usage-purposes/BACKEND_DEVELOPMENT/posts";

    @Autowired
    private ProductCategoryRepository productCategoryRepository;

    @Autowired
    private UsagePurposeRepository usagePurposeRepository;

    @Test
    void 게시판에_글을_작성하면_목록에서_찾아_상세를_조회할_수_있다() {
        // given
        ProductCategory category = productCategoryRepository.save(ProductCategory.from(ProductCategoryCode.LAPTOP));
        usagePurposeRepository.save(UsagePurpose.of(category, UsagePurposeCode.BACKEND_DEVELOPMENT));
        String title = "메모리는 얼마나 필요한가요?";
        String content = "도커를 함께 사용하려고 합니다.";

        // when: 로그인 없이 게시글을 작성한다.
        Response created = given()
            .contentType(ContentType.JSON)
            .body(new BoardPostCreateRequest(title, content))
            .when().post(BOARD_PATH)
            .then().statusCode(201)
            .extract().response();
        long id = created.jsonPath().getLong("id");
        String detailPath = created.header("Location");

        // then: 게시판 목록에서 방금 작성한 글을 찾는다.
        assertThat(detailPath).isEqualTo("/api/board-posts/" + id);
        given()
            .when().get(BOARD_PATH)
            .then().statusCode(200)
            .body("content.id", contains((int) id))
            .body("content.title", contains(title))
            .body("page", is(0))
            .body("size", is(20))
            .body("hasNext", is(false));

        // then: 목록에서 찾은 글의 상세 주소로 본문을 조회한다.
        given()
            .when().get(detailPath)
            .then().statusCode(200)
            .body("id", is((int) id))
            .body("title", is(title))
            .body("content", is(content))
            .body("createdAt", notNullValue())
            .body("categoryCode", is("LAPTOP"))
            .body("purposeCode", is("BACKEND_DEVELOPMENT"));
    }
}
