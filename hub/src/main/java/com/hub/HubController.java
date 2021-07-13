package com.hub;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.*;

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
    private Long leader;

    public HubController(HubRepository repository) throws IOException {
        this.repository = repository;
        this.hub = new ServerHub();
        if(!repository.findAll().isEmpty()) {
            leader = findLeader();
            sendLeader();
        }
        else {
            leader = null;
        }

    }

    @PostConstruct
    private void mapSetUp(){
        List<HubEntry> servers = repository.findAll();
        for (HubEntry entry : servers) {
            this.hub.addServer(entry.getId(), entry.getServerAddress());
        }
    }
    private Long findLeader() throws IOException {
        for(Long id: hub.getMap().keySet()) {
            URL url = new URL(hub.getMap().get(id) + "/ping");
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            con.setRequestProperty("accept", "application/json");
            con.setDoOutput(true);

            DataInputStream inputStream = (DataInputStream) con.getInputStream();
            if(inputStream.readBoolean())
            {
                return id;
            }
        }
        return null;
    }

    @PostMapping("/hub")
    protected Long addServer(@RequestBody String json) throws Exception {
        String address = parseAddressFromJsonString(json);

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
        if(leader == null)
        {
            leader = serverId;
        }

        sendLeader();

        return serverId;
    }

    private void sendLeader() throws IOException {
        for(String address : hub.getMap().values())
        {
            URL url = new URL(address + "/leader");
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("PUT");
            con.setRequestProperty("Content-Type", "application/json");
            con.setDoOutput(true);
            try(DataOutputStream os = (DataOutputStream) con.getOutputStream()) {
                os.writeLong(leader);
            }
            int y = con.getResponseCode();
            con.disconnect();
        }
    }


    @GetMapping("/hub")
    protected String getMap() {
        Gson gson = new Gson();
        return gson.toJson(this.hub.getMap());
    }

    @GetMapping("/hub/leader")
    protected Long getLeader() throws IOException {
        if(leader == null){
            leader = findLeader();
        }
        return leader;
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
            con.disconnect();
        }
    }

    @PutMapping("/hub")
    protected void updateAddress(@RequestBody String json) throws Exception {
        JsonParser parser = new JsonParser();
        JsonObject jsonObject = parser.parse(json).getAsJsonObject();
        Long id = jsonObject.get("id").getAsLong();
        String address = jsonObject.get("address").getAsString();
        this.hub.addServer(id, address);
        HubEntry entry = repository.getById(id);
        entry.setServerAddress(address);
        repository.save(entry);
        addNewServerToAllServers(json);
    }

    @DeleteMapping("/hub/{serverID}")
    protected void removeServerFromNetwork(@PathVariable Long serverID) throws Exception{
        if(!this.hub.removeServer(serverID)) return;
        repository.deleteById(serverID);
        for (Long id : this.hub.getMap().keySet()) {
            URL url = new URL(this.hub.getAddress(id));
            url = new URL("http://" + url.getHost() + "/bookstores/map");
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
            con.disconnect();
        }
    }

}