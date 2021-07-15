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

    // For GET requests
    public static HttpURLConnection createGetConnection(String address, String serverAddress, Long id) throws Exception {
        URL url = new URL(address);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        con.setRequestProperty("accept", "application/json");
        con.setRequestProperty("id", String.valueOf(id));
        con.setRequestProperty("referer", serverAddress);
        con.setDoOutput(true);
        con.connect();
        int x = con.getResponseCode();
        return con;
    }

    // For POST, PUT, and DELETE requests
    public static HttpURLConnection createConnection(String address, String request) throws Exception {
        URL url = new URL(address);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod(request);
        con.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        con.setRequestProperty("accept", "*/*");
        con.setRequestProperty("accept-encoding", "gzip,deflate,br");
        con.setUseCaches(false);
        con.setDoOutput(true);
        con.connect();
        return con;
    }


    public static HttpResponse<String> getPostConnectionJava9(String address, String json) throws URISyntaxException, java.io.IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(address))
                .headers("Content-Type", "application/json;charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return HttpClient.newBuilder()
                .build()
                .send(request, HttpResponse.BodyHandlers.ofString());
    }

    public static void outputJson(HttpURLConnection con, Gson gson, JsonObject json) throws IOException {
        String str = gson.toJson(json);
        try(OutputStream os = con.getOutputStream()) {
            byte[] input = str.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }
        int y = con.getResponseCode();
        con.disconnect();
    }
}
