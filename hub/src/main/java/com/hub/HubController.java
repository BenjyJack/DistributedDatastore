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
        for (Long x : this.hub.getMap().keySet()) {
            //Send the newly created server to the pre-existing servers
            URL url = new URL(this.hub.getMap().get(x));
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json");
            con.setDoOutput(true);
            DataOutputStream out = new DataOutputStream(con.getOutputStream());
            out.writeBytes(json);
            int y = con.getResponseCode();
            out.flush();
            out.close();

            //Send all pre-existing servers to the newly created server
            url = new URL(address);
            con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json");
            con.setDoOutput(true);
            Gson gson = new Gson();
            jso = new JsonObject();
            jso.addProperty("id", x);
            jso.addProperty("address", this.hub.getMap().get(x));
            String str = gson.toJson(jso);
            out = new DataOutputStream(con.getOutputStream());
            out.writeBytes(str);
            out.flush();
            out.close();
        }
    }
    @GetMapping("/hub")
    protected String getMap(){
        Gson gson = new Gson();
        String json = gson.toJson(this.hub.getMap());
        return json;
    }
}
