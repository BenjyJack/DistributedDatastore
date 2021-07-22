package com.ds.datastore;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.Builder;
import java.time.Duration;

public class Utilities {

//Tried putting a Retry here. After running once, it would return to the method that called it and would not retry
// Therefore, we put the Retry on the more global method and that worked
    public static HttpResponse<String> createConnection(String address, JsonObject jso, String serverAddress, Long id, String requestType) throws Exception{
        Gson gson = new Gson();
        String json = gson.toJson(jso);
        Builder builder = HttpRequest.newBuilder()
            .uri(new URI(address))
            .headers("Content-Type", "application/json;charset=UTF-8");
        if (requestType.equals("GET")) {
                builder = builder
                        .setHeader("referer", serverAddress)
                        .setHeader("id", String.valueOf(id))
                        .timeout(Duration.ofSeconds(4))
                        .GET();
        }else if(requestType.equals("POST")){
                builder = builder.POST(HttpRequest.BodyPublishers.ofString(json));
        }else if(requestType.equals("PUT")){
                builder = builder.PUT(HttpRequest.BodyPublishers.ofString(json));
        }else if (requestType.equals("DELETE")) {
                builder = builder.DELETE();
        }
        HttpRequest request = builder.build();
        HttpResponse<String> response = HttpClient.newBuilder()
                .build()
                .send(request, HttpResponse.BodyHandlers.ofString());
        if(requestType.equals("POST") && response.statusCode() != 201){
                System.out.println(response.statusCode());
                throw new RuntimeException();
        }
        return response;
    }
}
