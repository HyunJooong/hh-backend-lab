package com.choo.hhbackendlab.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity(name = "PRODUCT")
@Getter
@NoArgsConstructor
@ToString
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    private String name; //상품명
    private String description; // 상품 설명

    @ManyToOne
    @JoinColumn(name = "category_id")
    Category category; // 카테고리

    private int price; //가격
    private int stock; //재고

    @Column(nullable = false)
    private long viewCount; //조회수

    private LocalDateTime createdAt; //상품 등록
    private LocalDateTime updatedAt; //상품 수정

    /**
     * 상품 생성자
     */
    public Product(String name, String description, Category category, int price, int stock) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.price = price;
        this.stock = stock;
        this.viewCount = 0;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 재고를 차감합니다.
     * @param quantity 차감할 수량
     * @throws IllegalArgumentException 재고가 부족한 경우
     */
    public void removeStock(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("차감 수량은 0보다 커야 합니다.");
        }
        if (this.stock < quantity) {
            throw new IllegalArgumentException("재고가 부족합니다. 현재 재고: " + this.stock + ", 요청 수량: " + quantity);
        }
        this.stock -= quantity;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 재고를 적재합니다.
     * @param quantity 적재할 수량
     * @throws IllegalArgumentException 적재 수량이 유효하지 않은 경우
     */
    public void addStock(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("적재 수량은 0보다 커야 합니다.");
        }
        this.stock += quantity;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 조회수를 증가시킵니다.
     */
    public void incrementViewCount() {
        this.viewCount++;
    }
}
