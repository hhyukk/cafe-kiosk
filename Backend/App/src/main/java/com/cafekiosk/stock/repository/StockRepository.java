package com.cafekiosk.stock.repository;

import com.cafekiosk.stock.entity.Stock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StockRepository extends JpaRepository<Stock, Long> {
    Optional<Stock> findByMenuId(Long menuId);
}
