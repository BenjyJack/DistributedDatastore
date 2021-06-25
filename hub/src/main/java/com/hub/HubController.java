package com.hub;

import org.hibernate.annotations.Parameter;
import org.springframework.data.repository.query.Param;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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
    protected void addServer(@RequestBody String json) throws Exception {
        JsonObject jso = new JsonParser().parse(json).getAsJsonObject();
        Long id = jso.getAsJsonObject().get("id").getAsLong();
        String address = jso.getAsJsonObject().get("address").getAsString(); 
        this.hub.addServer(id, address);
        for (String x : this.hub.getMap().values()) {
            URL url = new URL(x);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json");
            con.setDoOutput(true);
            DataOutputStream out = new DataOutputStream(con.getOutputStream());
            out.writeBytes("{id:" + id +", \naddress:" + address +"}");
            out.flush();
            out.close();
        }
        URL url = new URL(address);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/json");
        con.setDoOutput(true);
        DataOutputStream out = new DataOutputStream(con.getOutputStream());
        for (Long x : this.hub.getMap().keySet()) {
            out.writeBytes("{id:" + x +", \naddress:" + this.hub.getMap().get(x) +"}");
        }
        // Gson gson = new Gson();
        // String json = gson.toJson(this.hub.getMap());
        // out.writeBytes(json);
        out.flush();
        out.close();

    }
    @GetMapping("/hub")
    protected String getMap(){
        Gson gson = new Gson();
        String json = gson.toJson(this.hub.getMap());
        return json;
    }



}
