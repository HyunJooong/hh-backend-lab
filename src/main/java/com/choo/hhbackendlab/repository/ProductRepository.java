package com.choo.hhbackendlab.repository;

import com.choo.hhbackendlab.entity.Product;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository <Product, Long> {

    /**
     * 조회수 순으로 상품 조회 (인기 상품)
     */
    @Query("SELECT p FROM PRODUCT p ORDER BY p.viewCount DESC")
    List<Product> findTopProductsByViewCount(Pageable pageable);

    /**
     * 최근 7일간 판매량 순으로 상품 조회 (인기 상품)
     * OrderItem과 JOIN하여 최근 7일간의 quantity를 합산하여 정렬
     */
    @Query("SELECT p FROM PRODUCT p " +
           "LEFT JOIN ORDER_ITEM oi ON oi.product.id = p.id " +
           "LEFT JOIN ORDERS o ON oi.order.id = o.id " +
           "WHERE o.orderedAt >= :weekAgo OR o.orderedAt IS NULL " +
           "GROUP BY p.id " +
           "ORDER BY SUM(COALESCE(oi.quantity, 0)) DESC")
    List<Product> findTopProductsBySalesInLastWeek(@org.springframework.data.repository.query.Param("weekAgo") java.time.LocalDateTime weekAgo, Pageable pageable);

    /**
     * 상품 조회수를 증가시킵니다. (벌크 업데이트)
     * @param productId 상품 ID
     * DB 부하를 줄이이기 위해 Redis로 구현 예정..
     */
    @Modifying
    @Query("UPDATE PRODUCT p SET p.viewCount = p.viewCount + 1 WHERE p.id = :productId")
    void incrementViewCount(@Param("productId") Long productId);
}
