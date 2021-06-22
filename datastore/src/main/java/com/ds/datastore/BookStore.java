package com.ds.datastore;

import javax.persistence.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

@Entity
public class BookStore {

    @Id
    @Column(name = "id", nullable = false)
    @GeneratedValue( strategy=GenerationType.AUTO )
    private long id;

    @Column(name = "name", length = 50)
    private String name;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "store_id")
    Set<Book> books = new HashSet<>();

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName( ){
        return name;
    }

    public void setName( String storeName ){
        this.name = storeName;
    }

    public void addBook(Book book)
    {
        books.add(book);
    }

}
