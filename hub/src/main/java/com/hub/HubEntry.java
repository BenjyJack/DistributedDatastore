package com.hub;

import javax.persistence.*;

@Entity
public class HubEntry {


    @Id
    @Column(name = "Id", nullable = false)
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    @Column(name = "Server Address", nullable = false)
    private String serverAddress;

    public String getServerAddress() {
        return serverAddress;
    }
    public void setServerAddress(String serverAddress) {
        this.serverAddress = serverAddress;
    }

    public long getId() {
        return id;
    }
    public void setId(long id) {
        this.id = id;
    }
}