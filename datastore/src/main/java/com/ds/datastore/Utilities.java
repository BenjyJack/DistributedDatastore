package com.ds.datastore;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.Builder;
import java.time.Duration;
import java.util.Optional;


@Component
public class Utilities {

    @Retry(name = "retry")
    public HttpResponse<String> createConnection(String address, JsonObject jso, String serverAddress, Long id, String requestType) throws Exception{
        System.out.println("utilities called");
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
        if(requestType.equals("POST") && (response.statusCode() != 201 && response.statusCode() != 200) ){
                System.out.println(response.statusCode());
                throw new RuntimeException();
        }
        return response;
    }

    @Retry(name = "retry")
    @CircuitBreaker(name = "#root.args[0]", fallbackMethod = "fallback")
    public Optional<HttpResponse<String>> createConnectionCircuitBreaker(String address, JsonObject jso, String serverAddress, Long id, String requestType) throws Exception{
        System.out.println("utilities called");
        HttpResponse<String> response = createConnection(address, jso, serverAddress, id, requestType);
        return Optional.ofNullable(response);
    }

    private Optional<HttpResponse<String>> fallback(String address, JsonObject jso, String serverAddress, Long id, String requestType, RuntimeException e) {
        System.out.println("falling back");
        // if(this.fallbackAddress == null){
        //         this.fallbackAddress = address;
        // }else{
        //         if(!this.fallbackAddress.equals(address)){
        //                 this.fallbackAddress = address;
        //                 try {
        //                         return Optional.of(createConnection(address, jso, serverAddress, id, requestType));
        //                 } catch (Exception e1) {
        //                 }  
        //         }
        // }
        return Optional.empty();
    }
}
