package com.wrb.devica.product.repository;

import com.wrb.devica.product.domain.ProductMinPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductMinPriceRepository extends JpaRepository<ProductMinPrice, Long> {

    @Modifying
    @Query(value = "DELETE FROM product_min_price WHERE product_id = :productId", nativeQuery = true)
    void deleteForProduct(@Param("productId") Long productId);

    // 품절 판매처는 마지막 확인 가격으로 함께 비교한다. 숨김·단종 판매처는 제외한다.
    @Modifying
    @Query(value = """
        INSERT INTO product_min_price (product_id, min_price)
        SELECT product_id, MIN(price)
        FROM product_offer
        WHERE product_id = :productId AND status IN ('ON_SALE', 'SOLD_OUT')
        GROUP BY product_id
        """, nativeQuery = true)
    void insertForProduct(@Param("productId") Long productId);

    default void refresh(Long productId) {
        deleteForProduct(productId);
        insertForProduct(productId);
    }
}
