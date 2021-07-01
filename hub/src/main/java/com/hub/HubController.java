package com.hub;

import org.hibernate.annotations.Parameter;
import org.springframework.data.repository.query.Param;
import org.springframework.web.bind.annotation.*;

import java.io.OutputStream;
import java.util.HashMap;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.annotation.PostConstruct;
import java.io.DataOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

@RestController
public class HubController {
    private ServerHub hub;
    private final HubRepository repository;

    @PostConstruct
    private void mapSetUp(){
        List<HubEntry> servers = repository.findAll();
        for (HubEntry entry: servers) {
            this.hub.addServer(entry.getId(), entry.getServerAddress());
        }
    }
    public HubController(HubRepository repository){
        this.repository = repository;
        this.hub = new ServerHub();
    }

    @PostMapping("/hub")
    protected void addServer(@RequestBody String json) throws Exception {
        JsonObject jso = new JsonParser().parse(json).getAsJsonObject();
        String address = jso.getAsJsonObject().get("address").getAsString();
        HubEntry server = new HubEntry();
        address = address + server.getId();
        server.setServerAddress(address);
        this.hub.addServer(server.getId(), address);
        Gson gson = new Gson();
        JsonObject jsobj = new JsonObject();
        // jso.addProperty("id", bookStore.getId());
        jsobj.addProperty("address", address);
        String str = gson.toJson(json);
        for (Long x : this.hub.getMap().keySet()) {
            //Send the newly created server to the pre-existing servers
            URL url = new URL(this.hub.getMap().get(x));
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json");
            con.setDoOutput(true);
            try(OutputStream os = con.getOutputStream()) {
                byte[] input = str.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            int y = con.getResponseCode();

//            //Send all pre-existing servers to the newly created server
//            url = new URL(address);
//            con = (HttpURLConnection) url.openConnection();
//            con.setRequestMethod("POST");
//            con.setRequestProperty("Content-Type", "application/json");
//            con.setDoOutput(true);
//            Gson gson = new Gson();
//            jso = new JsonObject();
//            jso.addProperty("id", x);
//            jso.addProperty("address", this.hub.getMap().get(x));
//            String str = gson.toJson(jso);
//            out = new DataOutputStream(con.getOutputStream());
//            out.writeBytes(str);
//            out.flush();
//            out.close();
            System.out.println(y);
        }
        //TODO make response
    }
    @GetMapping("/hub")
    protected String getMap(){
        Gson gson = new Gson();
        String json = gson.toJson(this.hub.getMap());
        return json;
    }
}
