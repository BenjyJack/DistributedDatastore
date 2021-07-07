package com.hub;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.OutputStream;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.annotation.PostConstruct;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
public class HubController {

    private final ServerHub hub;
    private final HubRepository repository;

    public HubController(HubRepository repository){
        this.repository = repository;
        this.hub = new ServerHub();
    }

    @PostConstruct
    private void mapSetUp(){
        List<HubEntry> servers = repository.findAll();
        for (HubEntry entry : servers) {
            this.hub.addServer(entry.getId(), entry.getServerAddress());
        }
    }

    @PostMapping("/hub")
    protected Long addServer(@RequestBody String json) throws Exception {
        String address = parseAddressFromJsonString(json);

        //TODO: למעשה, do we really need to set the server address twice and save twice?
        HubEntry server = new HubEntry();
        server.setServerAddress(address);
        repository.save(server);
        address = address + server.getId();
        server.setServerAddress(address);
        repository.save(server);

        Long serverId = server.getId();
        String newServerInfo = storeServerInfoInString(serverId, address);
        this.hub.addServer(serverId, address);
        addNewServerToAllServers(newServerInfo);
        return serverId;
    }

    @GetMapping("/hub")
    protected String getMap() {
        Gson gson = new Gson();
        return gson.toJson(this.hub.getMap());
    }

    private String parseAddressFromJsonString(String json) {
        JsonParser parser = new JsonParser();
        JsonObject jsonObject = parser.parse(json).getAsJsonObject();
        return jsonObject.get("address").getAsString();
    }

    private String storeServerInfoInString(Long id, String address) {
        Gson gson = new Gson();
        JsonObject newServerAsJson = new JsonObject();
        newServerAsJson.addProperty("id", id);
        newServerAsJson.addProperty("address", address);
        return gson.toJson(newServerAsJson);
    }

    private void addNewServerToAllServers(String serverInfo) throws Exception {
        for (Long id : this.hub.getMap().keySet()) {
            //Send the newly created server to the pre-existing servers
            URL url = new URL(this.hub.getAddress(id));
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json");
            con.setDoOutput(true);
            try(OutputStream os = con.getOutputStream()) {
                byte[] input = serverInfo.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            int y = con.getResponseCode();
            System.out.println(y);
        }
    }
    @DeleteMapping("/hub/{serverID}")
    protected void removeServerFromNetwork(@PathVariable Long serverID) throws Exception{
        if(!this.hub.removeServer(serverID)) return;
        repository.deleteById(serverID);
        for (Long id : this.hub.getMap().keySet()) {
            URL url = new URL(this.hub.getAddress(id));
            url = new URL("http://" + url.getHost() + "/bookstores");
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("DELETE");
            con.setRequestProperty("Content-Type", "application/json");
            con.setDoOutput(true);
            Gson gson = new Gson();
            JsonObject json = new JsonObject();
            json.addProperty("id", serverID);
            String str = gson.toJson(json);
            try(OutputStream os = con.getOutputStream()) {
                byte[] input =str.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            int y = con.getResponseCode();
            System.out.println(y);
        }
    }

}
