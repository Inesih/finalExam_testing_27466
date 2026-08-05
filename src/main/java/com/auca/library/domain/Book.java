package com.auca.library.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "books")
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    private String title;
    private String author;

    @Enumerated(EnumType.STRING)
    private EBookStatus status;

    @ManyToOne
    @JoinColumn(name = "shelf_id")
    private Shelf shelf;

    public Book() {
    }

    public Book(String title, String author, EBookStatus status, Shelf shelf) {
        this.title = title;
        this.author = author;
        this.status = status;
        this.shelf = shelf;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public EBookStatus getStatus() {
        return status;
    }

    public void setStatus(EBookStatus status) {
        this.status = status;
    }

    public Shelf getShelf() {
        return shelf;
    }

    public void setShelf(Shelf shelf) {
        this.shelf = shelf;
    }
}