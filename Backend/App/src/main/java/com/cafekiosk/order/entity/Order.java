package com.cafekiosk.order.entity;

import com.cafekiosk.global.jpa.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static jakarta.persistence.FetchType.LAZY;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class Order extends BaseEntity {

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;
    
    private LocalDateTime orderTime;

    private String address;
    private int postcode;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> orderItems = new ArrayList<>();

    public Order(Customer customer, LocalDateTime orderTime, String address, int postcode) {
        setCustomer(customer);
        this.orderTime = orderTime;
        this.address = address;
        this.postcode = postcode;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
        if (customer != null && !customer.getOrders().contains(this)) {
            customer.getOrders().add(this);
        }
    }
}
