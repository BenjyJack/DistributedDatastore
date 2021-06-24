package com.hub;

import org.springframework.web.bind.annotation.*;
import java.util.HashMap;

import com.google.gson.Gson;

import java.io.DataOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

@RestController
public class HubController {
    private ServerHub hub;
    public HubController(){
        this.hub = new ServerHub();
    }

    @PostMapping("/hub")
    protected void addServer(Long id, String address) throws Exception {
        this.hub.addServer(id, address);
        for (String x : this.hub.getMap().values()) {
            URL url = new URL(x);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json");
            DataOutputStream out = new DataOutputStream(con.getOutputStream());
            out.writeBytes("{\"id\":" + id +", \n\"address:\":" + address +"}");
            out.flush();
            out.close();
        }
        URL url = new URL(address);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/json");
        DataOutputStream out = new DataOutputStream(con.getOutputStream());
        Gson gson = new Gson();
        String json = gson.toJson(this.hub.getMap());
        out.writeBytes(json);
        out.flush();
        out.close();

    }
    @GetMapping("/hub")
    protected HashMap getMap(){
        return this.hub.getMap();
    }



}
