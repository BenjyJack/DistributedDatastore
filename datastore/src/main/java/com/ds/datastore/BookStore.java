package com.ds.datastore;

import javax.persistence.*;
import java.util.List;
import java.util.ArrayList;

@Entity
public class BookStore {

    @Id
    @Column(name = "Id", nullable = false)
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    @Column(name = "Name")
    private String name;
    @Column(name = "Phone")
    private String phone;
    @Column(name = "Address")
    private String streetAddress;

    @OneToMany(mappedBy = "store", cascade = CascadeType.ALL, orphanRemoval = true)
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

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getStreetAddress() {
        return streetAddress;
    }

    public void setStreetAddress(String streetAddress) {
        this.streetAddress = streetAddress;
    }
}
