package com.hub;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.OutputStream;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.annotation.PostConstruct;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
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
    protected Long addServer(@RequestBody String json) throws Exception {
        JsonObject jso = new JsonParser().parse(json).getAsJsonObject();
        String address = jso.getAsJsonObject().get("address").getAsString();
        HubEntry server = new HubEntry();
        server.setServerAddress(address);
        repository.save(server);
        address = address + server.getId();
        server.setServerAddress(address);
        repository.save(server);
        Gson gson = new Gson();
        JsonObject jsObj = new JsonObject();
        jsObj.addProperty("id", server.getId());
        jsObj.addProperty("address", address);
        String str = gson.toJson(jsObj);
        for (Long x : this.hub.getMap().keySet()) {
            //Send the newly created server to the pre-existing servers
            URL url = new URL(this.hub.getMap().get(x));
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json");
            con.setDoOutput(true);
            try(OutputStream os = con.getOutputStream()) {
                byte[] input = str.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int y = con.getResponseCode();
            System.out.println(y);
        }
        this.hub.addServer(server.getId(), address);
        return server.getId();
    }

    @GetMapping("/hub")
    protected String getMap(){
        Gson gson = new Gson();
        String json = gson.toJson(this.hub.getMap());
        return json;
    }
}
