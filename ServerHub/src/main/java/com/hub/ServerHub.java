package com.hub;


import java.util.HashMap;

public class ServerHub {
    private final HashMap<Long, String> serverToHTTP;
    public ServerHub() {
        serverToHTTP = new HashMap<>();
    }
    public void addServer(Long id, String address){
        serverToHTTP.put(id, address);
    }
    public String getAddress(Long id){
        return serverToHTTP.get(id);
    }
}
