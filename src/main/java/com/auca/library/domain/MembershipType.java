package com.auca.library.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "membership_types")
public class MembershipType {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    private String name;
    private int maxBooks;
    private int price;
    private int dailyRate;

    public MembershipType() {}

    public MembershipType(String name, int maxBooks, int price, int dailyRate) {
        this.name = name;
        this.maxBooks = maxBooks;
        this.price = price;
        this.dailyRate = dailyRate;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getMaxBooks() { return maxBooks; }
    public void setMaxBooks(int maxBooks) { this.maxBooks = maxBooks; }

    public int getPrice() { return price; }
    public void setPrice(int price) { this.price = price; }

    public int getDailyRate() { return dailyRate; }
    public void setDailyRate(int dailyRate) { this.dailyRate = dailyRate; }
}