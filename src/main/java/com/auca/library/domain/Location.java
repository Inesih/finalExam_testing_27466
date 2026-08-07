package com.auca.library.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "locations")
public class Location {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String code;

    private String name;

    @Enumerated(EnumType.STRING)
    private ELocationType type;

    @ManyToOne
    @JoinColumn(name = "parent_id")
    private Location parent;

    public Location() {
    }

    public Location(String code, String name, ELocationType type, Location parent) {
        this.code = code;
        this.name = name;
        this.type = type;
        this.parent = parent;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public ELocationType getType() { return type; }
    public void setType(ELocationType type) { this.type = type; }

    public Location getParent() { return parent; }
    public void setParent(Location parent) { this.parent = parent; }
}