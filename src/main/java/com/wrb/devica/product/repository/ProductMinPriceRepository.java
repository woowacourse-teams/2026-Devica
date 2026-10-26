package com.wrb.devica.product.repository;

import com.wrb.devica.product.domain.ProductMinPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ProductMinPriceRepository extends JpaRepository<ProductMinPrice, Long> {

    @Modifying
    @Query(value = "DELETE FROM product_min_price WHERE product_id = :productId", nativeQuery = true)
    void deleteForProduct(@Param("productId") Long productId);

    @Modifying
    @Query(value = """
        INSERT INTO product_min_price (product_id, min_price)
        SELECT product_id, MIN(price)
        FROM product_offer
        WHERE product_id = :productId AND status = 'ON_SALE'
        GROUP BY product_id
        """, nativeQuery = true)
    void insertForProduct(@Param("productId") Long productId);

    @Transactional
    default void refresh(Long productId) {
        deleteForProduct(productId);
        insertForProduct(productId);
    }
}
