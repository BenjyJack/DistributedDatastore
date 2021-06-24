package com.ds.datastore;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import javax.persistence.*;

@JsonIgnoreProperties(value = {"store"})
@Entity
public class Book {

    private @Id @GeneratedValue Long id;
    private String author;
    private String category;
    private String title;
    private double price = -1;
    private String description;
    private Language language;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "books")
    private BookStore store;
    private Long storeID;

    public Book() {}

    public void setStoreID(Long storeID) {
        this.storeID = storeID;
    }
    public Long getStoreID() {
        return storeID;
    }

    public BookStore getStore() {
        return store;
    }
    public void setStore(BookStore store) {
        this.store = store;
        this.storeID = store.getId();
    }

    public String getAuthor() {
        return author;
    }
    public void setAuthor(String author){
        this.author = author;
    }

    public String getCategory() {
        return category;
    }
    public void setCategory(String category) {
        this.category = category;
    }

    public String getTitle() {
        return title;
    }
    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }
    public void setDescription(String description) {
        this.description = description;
    }

    public Language getLanguage() {
        return language;
    }
    public void setLanguage(Language language) {
        this.language = language;
    }

    public double getPrice() {
        return price;
    }
    public void setPrice(double price) {
        this.price = price;
    }

    public void setId(Long id) {
        this.id = id;
    }
    public Long getId() {
        return id;
    }

}
