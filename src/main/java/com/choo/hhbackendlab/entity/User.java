package com.choo.hhbackendlab.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity(name = "USER")
@Getter
@NoArgsConstructor
@ToString
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private String username;
    private String email;
    private String password;
    private int balance; // 잔액 기반 결제(포인트)
    private LocalDateTime registerAt;
    private LocalDateTime updateAt;

    @OneToMany(mappedBy = "user")
    private List<Order> orders = new ArrayList<>();

    // 포인트 충전
    public void chargeBalance(int amount) {
        this.balance += amount;
    }

    // 포인트 사용
    public void reduceBalance(int amount) {
        if (this.balance < amount) {
            throw new IllegalStateException("잔액 부족");
        }
        this.balance -= amount;
    }


}
