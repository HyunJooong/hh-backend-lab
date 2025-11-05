package com.choo.hhbackendlab.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity(name = "POINT_WALLET")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointWallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private int balance;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Version
    private Long version;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // 생성자
    public PointWallet(User user, int initialBalance) {
        if (user == null) {
            throw new IllegalArgumentException("사용자를 확인할 수 없습니다.");
        }
        if (initialBalance < 0) {
            throw new IllegalArgumentException("금액을 확인해 주세요.");
        }
        this.user = user;
        this.balance = initialBalance;
    }

    // 포인트 충전
    public void charge(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("충전금을 확인해 주세요.");
        }
        this.balance += amount;
    }

    // 포인트 사용
    public void use(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("금액을 확인해주세요.");
        }
        if (this.balance < amount) {
            throw new IllegalStateException("잔액이 부족합니다.: " + this.balance + ", Required: " + amount);
        }
        this.balance -= amount;
    }

    // 잔액 확인
    public boolean hasEnoughBalance(int amount) {
        return this.balance >= amount;
    }

    // 포인트 환불
    public void refund(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("환불 금액을 확인해주세요.");
        }
        this.balance += amount;
    }
}
