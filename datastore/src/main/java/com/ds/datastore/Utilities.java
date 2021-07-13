package com.ds.datastore;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
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
        con.setRequestProperty("Content-Type", "application/json");
        con.setDoOutput(true);
        con.connect();
        return con;
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
