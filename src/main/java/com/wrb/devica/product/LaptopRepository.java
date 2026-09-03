package com.wrb.devica.product;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LaptopRepository extends JpaRepository<Laptop, Long>, LaptopRepositoryCustom {
}
