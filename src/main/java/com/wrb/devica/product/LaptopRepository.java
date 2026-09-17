package com.wrb.devica.product;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LaptopRepository extends JpaRepository<Laptop, Long>, LaptopRepositoryCustom {

    @EntityGraph(attributePaths = "cpu")
    Optional<Laptop> findWithCpuById(Long id);
}
