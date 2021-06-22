package com.ds.datastore;

import javax.persistence.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

@Entity (name = "BOOKSTORE")
public class BookStore {

    @Id
    @Column(name = "STORE_ID", nullable = false)
    @GeneratedValue( strategy=GenerationType.AUTO )
    private long id;

    @Column(name = "STORE_NAME", length = 50)
    private String name;

    @OneToMany(targetEntity=Book.class, mappedBy="store", fetch=FetchType.EAGER)
    @Column(name = "INVENTORY")
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

}
