package com.choo.hhbackendlab.repository;

import com.choo.hhbackendlab.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    /**
     * 주문번호로 주문 조회
     */
    Optional<Order> findByOrderNumber(String orderNumber);

    /**
     * 주문번호로 주문 조회 (주문 아이템과 상품 정보 포함)
     * Fetch Join을 사용하여 N+1 문제 해결
     */
    @Query("SELECT o FROM ORDERS o " +
           "JOIN FETCH o.orderItems oi " +
           "JOIN FETCH oi.product " +
           "WHERE o.orderNumber = :orderNumber")
    Optional<Order> findByOrderNumberWithItems(@Param("orderNumber") String orderNumber);
}
