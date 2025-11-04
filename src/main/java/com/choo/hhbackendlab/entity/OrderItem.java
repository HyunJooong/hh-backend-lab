package com.choo.hhbackendlab.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity(name = "ORDER_ITEM")
@Getter
@NoArgsConstructor
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private int quantity;  // 주문 수량

    @Column(nullable = false)
    private int price;  // 상품 가격

    @Column(nullable = false)
    private int totalPrice;  // 총 금액 (price * quantity)

    @Builder
    public OrderItem(Order order, Product product, int quantity, int price) {
        this.product = product;
        this.quantity = quantity;
        this.price = price;
        this.totalPrice = price * quantity;
        setOrder(order);
    }

    /**
     * 연관관계 편의 메서드 - Order와의 양방향 관계 설정
     */
    public void setOrder(Order order) {
        if (this.order != null) {
            this.order.getOrderItems().remove(this);
        }
        this.order = order;
        if (order != null && !order.getOrderItems().contains(this)) {
            order.getOrderItems().add(this);
        }
    }
}