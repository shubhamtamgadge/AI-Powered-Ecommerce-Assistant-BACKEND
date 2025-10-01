package com.shubham.myAi.repository;

import com.shubham.myAi.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product,Long> {

    List<Product> findByCategoryAndPriceLessThan(String category, double price);
}
