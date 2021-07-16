package com.hub;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;

import java.io.*;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.annotation.PostConstruct;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;


@RestController
@EnableScheduling
public class HubController {

    private final ServerHub hub;
    private final HubRepository repository;
    private String leader;

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

    protected String findLeader() throws IOException {
        leader = null;
        for(Long id: hub.getMap().keySet()) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(new URI(this.hub.getAddress(id) + "/ping"))
                        .headers("Content-Type", "application/json;charset=UTF-8")
                        .GET()
                        .build();
                HttpResponse<String> response = HttpClient.newBuilder()
                        .build()
                        .send(request, HttpResponse.BodyHandlers.ofString());
                if(response.body().equals("true")){
                    leader = String.valueOf(id);
                    return leader;
                }
            }
            catch(Exception ignored) {
            }
        }
        return null;
    }
    protected String basicGetLeader(){
        return leader;
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
            leader = String.valueOf(serverId);
        }
        sendLeader();
        return serverId;
    }

    protected void sendLeader() throws IOException {
        for(String address : hub.getMap().values())
        {
            URL url = new URL(address + "/leader");
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("PUT");
            con.setRequestProperty("Content-Type", "application/json");
            con.setDoOutput(true);
            try(OutputStream os = con.getOutputStream()) {
                byte[] input = leader.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            int y = con.getResponseCode();
            con.disconnect();
        }
    }

    @Scheduled(fixedDelay = 60000)
    protected void randomlyCheck() throws Exception {
        if(this.leader != null){
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(this.hub.getAddress(Long.parseLong(leader)) + "/ping"))
                    .headers("Content-Type", "application/json;charset=UTF-8")
                    .timeout(Duration.ofMillis(300))
                    .GET()
                    .build();
            HttpResponse<String> response = HttpClient.newBuilder()
                    .build()
                    .send(request, HttpResponse.BodyHandlers.ofString());
            if(!response.body().equals("true")){
                findLeader();
                if(leader != null) sendLeader();
            }
        }else findLeader();
    }

    @GetMapping("/hub")
    protected String getMap() {
        Gson gson = new Gson();
        return gson.toJson(this.hub.getMap());
    }

    protected HashMap<Long, String> getLocalMap(){
        return this.hub.getMap();
    }

    @GetMapping("/hub/leader")
    protected String getLeader(@RequestHeader(name = "referer") String address, @RequestHeader(name = "id") String id) throws IOException {
        if(leader == null && !id.equals("null")){
            leader = id;
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
        if(serverID.equals(Long.parseLong(leader))){
            findLeader();
            sendLeader();
        }
    }
}
