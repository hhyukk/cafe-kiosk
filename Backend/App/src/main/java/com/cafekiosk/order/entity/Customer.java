package com.cafekiosk.order.entity;

import com.cafekiosk.global.jpa.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

import static jakarta.persistence.CascadeType.PERSIST;
import static jakarta.persistence.CascadeType.REMOVE;

@Entity
@Table(name = "customer")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class Customer extends BaseEntity {

    @Column(unique = true)
    private String email;

    @OneToMany(mappedBy = "customer", cascade = {PERSIST, REMOVE}, orphanRemoval = true)
    private List<Order> orders = new ArrayList<>();

    public Customer(String email) {
        this.email = email;
        this.orders = new ArrayList<>();
    }

}
