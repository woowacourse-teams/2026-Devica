package com.wrb.devica.product.repository;

import com.wrb.devica.product.domain.Laptop;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LaptopRepository extends JpaRepository<Laptop, Long>, LaptopRepositoryCustom {

    @EntityGraph(attributePaths = "cpu")
    @Query("""
        SELECT l FROM Laptop l WHERE l.id = :id AND (
            NOT EXISTS (SELECT o.id FROM ProductOffer o WHERE o.productId = l.id
                AND o.status = com.wrb.devica.product.domain.OfferStatus.HIDDEN)
            OR EXISTS (SELECT o.id FROM ProductOffer o WHERE o.productId = l.id
                AND o.status = com.wrb.devica.product.domain.OfferStatus.ON_SALE)
        )
        """)
    Optional<Laptop> findWithCpuById(Long id);
}
