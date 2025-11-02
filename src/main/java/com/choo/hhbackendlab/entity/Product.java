package com.choo.hhbackendlab.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
    private int price; //가격
    private int stock; //재고
    private LocalDateTime createdAt; //상품 등록
    private LocalDateTime updatedAt; //상품 수정
}
