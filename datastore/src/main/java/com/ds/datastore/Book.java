package com.ds.datastore;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

@Entity
public class Book {

    private @Id @GeneratedValue Long id;
    final String author;
    final String category;
    final String title;
    double price;
    final String description;
    final Lang lang;


    public Book() {
        author = null;
        category = null;
        title = null;
        description = null;
        lang = null;
        throw new BookNotFoundException(0L);
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public Book(String category, String author, String title, double price, Lang lang){
        this.author = author;
        this.category = category;
        this.title = title;
        this.price = price;
        this.description = null;
        this.lang = lang;
    }
    public Book(String category, String author, String title, double price, Lang lang, String description){
        this.author = author;
        this.category = category;
        this.title = title;
        this.price = price;
        this.description = description;
        this.lang = lang;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @Id
    public Long getId() {
        return id;
    }
}
