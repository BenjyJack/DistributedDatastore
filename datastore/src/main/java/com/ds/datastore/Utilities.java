package com.ds.datastore;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class Utilities {

    public static HttpResponse<String> createGetConnection(String address, String serverAddress, Long id) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(address))
                .headers("Content-Type", "application/json;charset=UTF-8")
                .setHeader(serverAddress, String.valueOf(id))
                .GET()
                .build();
        return HttpClient.newBuilder()
                .build()
                .send(request, HttpResponse.BodyHandlers.ofString());
    }

    public static HttpResponse<String> createPostConnection(String address, JsonObject jso) throws Exception {
        Gson gson = new Gson();
        String json = gson.toJson(jso);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(address))
                .headers("Content-Type", "application/json;charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return HttpClient.newBuilder()
                .build()
                .send(request, HttpResponse.BodyHandlers.ofString());
    }

    public static HttpResponse<String> createPutConnection(String address, JsonObject jso) throws Exception {
        Gson gson = new Gson();
        String json = gson.toJson(jso);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(address))
                .headers("Content-Type", "application/json;charset=UTF-8")
                .PUT(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return HttpClient.newBuilder()
                .build()
                .send(request, HttpResponse.BodyHandlers.ofString());
    }

    public static HttpResponse<String> createDeleteConnection(String address) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(address))
                .headers("Content-Type", "application/json;charset=UTF-8")
                .DELETE()
                .build();
        return HttpClient.newBuilder()
                .build()
                .send(request, HttpResponse.BodyHandlers.ofString());
    }
}
