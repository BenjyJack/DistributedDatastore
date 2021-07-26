package com.hub;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;

import java.io.*;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;


@RestController
@EnableScheduling
public class HubController {

    private final ServerHub hub;
    private final HubRepository repository;
    private String leader;
    Logger logger = LoggerFactory.getLogger(HubController.class);

    public HubController(HubRepository repository) throws IOException {
        this.repository = repository;
        this.hub = new ServerHub();
        leader = findAndSendLeader();
        logger.info("Hub initialized");
    }

    @PostConstruct
    private void mapSetUp(){
        List<HubEntry> servers = repository.findAll();
        for (HubEntry entry : servers) {
            this.hub.addServer(entry.getId(), entry.getServerAddress());
        }
    }

    protected String findAndSendLeader() throws IOException {
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
                    sendLeader();
                    logger.info(leader + " is now the leader");
                    return leader;
                }
            }
            catch(Exception ignored) {
            }
        }
        return null;
    }

    @RateLimiter(name = "DDoS-stopper")
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
        logger.info(address + " has been added to the network");
        return serverId;
    }

    private void sendLeader() throws Exception {
        for(String address : hub.getMap().values())
        {
            HttpRequest request = HttpRequest.newBuilder()
                        .uri(new URI(address + "/leader"))
                        .PUT(HttpRequest.BodyPublishers.ofString(leader))
                        .build();
            HttpResponse<String> response = HttpClient.newBuilder()
                        .build()
                        .send(request, HttpResponse.BodyHandlers.ofString());
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
            HttpResponse<String> response = null;
            try{
                response = HttpClient.newBuilder()
                        .build()
                        .send(request, HttpResponse.BodyHandlers.ofString());
            }catch(Exception e){
                findAndSendLeader();
            }
            if(!response.body().equals("true")){
                findAndSendLeader();
            }
        }else{
            findAndSendLeader();
        }
    }

    @RateLimiter(name = "DDoS-stopper")
    @GetMapping("/hub")
    protected String getMap() {
        Gson gson = new Gson();
        return gson.toJson(this.hub.getMap());
    }

    @RateLimiter(name = "DDoS-stopper")
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
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(this.hub.getAddress(id)))
                    .headers("Content-Type", "application/json;charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(serverInfo))
                    .build();
            HttpClient.newBuilder()
                    .build()
                    .send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    @RateLimiter(name = "DDoS-stopper")
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
        logger.info(id + "'s address has changed to " + address);
    }

    @RateLimiter(name = "DDoS-stopper")
    @DeleteMapping("/hub/{serverID}")
    protected void removeServerFromNetwork(@PathVariable Long serverID) throws Exception{
        if(!this.hub.removeServer(serverID)) return;
        repository.deleteById(serverID);
        for (Long id : this.hub.getMap().keySet()) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(this.hub.getAddress(id)))
                    .headers("Content-Type", "application/json;charset=UTF-8")
                    .DELETE()
                    .build();
            HttpClient.newBuilder()
                    .build()
                    .send(request, HttpResponse.BodyHandlers.ofString());
        }
        if(serverID.equals(Long.parseLong(leader))){
            findAndSendLeader();
        }
        logger.info(serverID + " has been removed from the network");
    }
}
