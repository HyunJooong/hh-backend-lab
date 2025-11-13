package com.choo.hhbackendlab.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity(name = "USERS")
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
    private LocalDateTime registerAt;
    private LocalDateTime updateAt;

    @OneToMany(mappedBy = "user")
    private List<Order> orders = new ArrayList<>();

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private PointWallet pointWallet;

    @OneToMany(mappedBy = "user")
    private List<UserCoupon> coupons = new ArrayList<>();

    /**
     * PointWallet 설정 (양방향 관계 편의 메서드)
     */
    public void setPointWallet(PointWallet pointWallet) {
        this.pointWallet = pointWallet;
    }

    /**
     * PointWallet 초기화
     */
    public void initializePointWallet(int initialBalance) {
        if (this.pointWallet != null) {
            throw new IllegalStateException("PointWallet이 이미 존재합니다.");
        }
        this.pointWallet = new PointWallet(this, initialBalance);
    }


}
