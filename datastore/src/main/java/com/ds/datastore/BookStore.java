package com.ds.datastore;

import javax.persistence.*;
import java.util.List;
import java.util.ArrayList;

@Entity
public class BookStore {

    @Id
    @Column(name = "id", nullable = false)
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    @Column(name = "name", length = 50)
    private String name;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "storeID")
    List<Book> books = new ArrayList<>();

    public long getId() {
        return id;
    }
    public void setId(long id) {
        this.id = id;
    }

    public String getName(){
        return name;
    }
    public void setName(String storeName){
        this.name = storeName;
    }

    public List<Book> getBooks() {
        return books;
    }

}
