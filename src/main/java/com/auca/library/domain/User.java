package com.auca.library.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User extends Person {

    private String username;
    private String password;

    @Enumerated(EnumType.STRING)
    private ERole role;

    public User() {
        super();
    }

    public User(String firstName, String lastName, String phoneNumber, EGender gender, Location location, String username, String password, ERole role) {
        super(firstName, lastName, phoneNumber, gender, location);
        this.username = username;
        this.password = password;
        this.role = role;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public ERole getRole() {
        return role;
    }

    public void setRole(ERole role) {
        this.role = role;
    }
}