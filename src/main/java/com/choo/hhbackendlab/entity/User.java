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
    private LocalDateTime registerAt;
    private LocalDateTime updateAt;

    @OneToMany(mappedBy = "user")
    private List<Order> orders = new ArrayList<>();


}
