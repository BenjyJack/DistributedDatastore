package com.ds.datastore;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import java.io.*;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.annotation.PostConstruct;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
public class BookStoreController {

    private final BookStoreModelAssembler assembler;
    private final BookRepository bookRepository;
    private final BookStoreRepository storeRepository;
    private ServerMap map;
    private Long id = null;

    @Value("${application.baseUrl}")
    private String url;

    public BookStoreController(BookStoreModelAssembler assembler, BookRepository bookRepository, BookStoreRepository storeRepository, ServerMap map) {
        this.assembler = assembler;
        this.bookRepository = bookRepository;
        this.storeRepository = storeRepository;
        this.map = map;
    }

    @PostConstruct
    private void restartChangedOrNew() throws Exception {
        this.map.setMap(reclaimMap());
        List<BookStore> bookStoreList = storeRepository.findAll();
        if(!bookStoreList.isEmpty()) {
            BookStore bookStore = bookStoreList.get(0);
            this.id = bookStore.getServerId();
            String serverAddress = this.map.get(this.id);
            if(!serverAddress.equals(url + "/bookstores/" + this.id)) {
                putToHub();
            }
        }
    }

    private void putToHub() throws Exception{
        URL hubAddress = new URL("http://71.172.193.59:8080/hub");
        HttpURLConnection con = (HttpURLConnection) hubAddress.openConnection();
        con.setRequestMethod("PUT");
        con.setRequestProperty("Content-Type", "application/json");
        con.setDoOutput(true);
        con.connect();
        DataOutputStream out = new DataOutputStream(con.getOutputStream());
        Gson gson = new Gson();
        JsonObject json = new JsonObject();
        json.addProperty("id", this.id);
        json.addProperty("address", this.url + "/bookstores/" + this.id);
        String str = gson.toJson(json);
        try(OutputStream os = con.getOutputStream()) {
            byte[] input = str.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }
        int y = con.getResponseCode();
    }

    private HashMap<Long, String> reclaimMap() throws Exception {
        URL url = new URL("http://71.172.193.59:8080/hub");
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        con.setRequestProperty("accept", "application/json");
        con.setDoOutput(true);
        con.connect();
        InputStream inStream = con.getInputStream();
        JsonReader reader = new JsonReader(new InputStreamReader(inStream, StandardCharsets.UTF_8));
        Gson gson = new Gson();
        Type type = new TypeToken<HashMap<Long, String>>(){}.getType();
        HashMap<Long, String> map = gson.fromJson(reader, type);
        inStream.close();
        reader.close();
        return map;
    }

    @PostMapping("/bookstores")
    protected ResponseEntity<EntityModel<BookStore>> newBookStore(@RequestBody BookStore bookStore) throws Exception {
        if (this.id != null) {
            return ResponseEntity.status(HttpStatus.ALREADY_REPORTED).build();
        }
        EntityModel<BookStore> entityModel = postToHub(bookStore);
        System.out.println(map.getMap().toString());
        return ResponseEntity
                .created(entityModel.getRequiredLink(IanaLinkRelations.SELF).toUri())
                .body(entityModel);
    }

    @PostMapping("/bookstores/{id}")
    protected void addServer(@RequestBody String json, @PathVariable String id) {
        JsonObject jso = new JsonParser().parse(json).getAsJsonObject();
        Long givenID = jso.get("id").getAsLong();
        String address = jso.get("address").getAsString();
        this.map.put(givenID, address);
    }

    @GetMapping("/bookstores/{storeID}")
    protected ResponseEntity<EntityModel<BookStore>> one(@PathVariable Long storeID) throws Exception {
        try{
            BookStore bookStore = storeRepository.findByServerId(storeID).orElseThrow(() -> new BookStoreNotFoundException(storeID));
            EntityModel<BookStore> entityModel = assembler.toModel(bookStore);
            return ResponseEntity
                    .created(entityModel.getRequiredLink(IanaLinkRelations.SELF).toUri())
                    .body(entityModel);
        }catch (BookStoreNotFoundException e){
            if(this.map.containsKey(storeID)){
                UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(this.map.get(storeID));
                URI uri = new URI(builder.toUriString());
                return ResponseEntity.status(HttpStatus.PERMANENT_REDIRECT).location(uri).build();
            }else{
                throw e;
            }
        }
    }

    @GetMapping("/bookstores")
    protected CollectionModel<EntityModel<BookStore>> all() throws Exception {
        List<EntityModel<BookStore>> entModelList = new ArrayList<>();
        for (Long id : this.map.keySet()) {
            URL url = new URL(this.map.get(id));
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            con.setRequestProperty("accept", "application/json");
            con.setDoOutput(true);
            con.connect();
            int x = con.getResponseCode();
            InputStream inStream = con.getInputStream();
            
            JsonParser jsonParser = new JsonParser();
            JsonObject jso = (JsonObject)jsonParser.parse(new InputStreamReader(inStream, "UTF-8"));
            BookStore store = new BookStore();
            store.setServerId((jso.get("serverId") != null ? jso.get("serverId").getAsLong() : null));
            store.setName(!jso.get("name").isJsonNull() ? jso.get("name").getAsString(): null);
            store.setPhone(!jso.get("phone").isJsonNull() ? jso.get("phone").getAsString(): null);
            store.setStreetAddress(!jso.get("streetAddress").isJsonNull() ? jso.get("streetAddress").getAsString(): null);
            //Not including the List of books contained in the store
            EntityModel<BookStore> entityModel = assembler.toModel(store);
            entModelList.add(entityModel);
        }
        return CollectionModel.of(entModelList, linkTo(methodOn(BookStoreController.class).all()).withSelfRel());
    }

    @PutMapping("/bookstores/{storeID}")
    protected BookStore updateBookStore(@RequestBody BookStore newBookStore, @PathVariable Long storeID) {
        return storeRepository.findById(storeID)
                .map(bookStore -> {
                    if(newBookStore.getName() != null) bookStore.setName(newBookStore.getName());
                    if(newBookStore.getPhone() != null) bookStore.setPhone(newBookStore.getPhone());
                    if(newBookStore.getStreetAddress() != null) bookStore.setStreetAddress(newBookStore.getStreetAddress());
                    return storeRepository.save(bookStore);
                })
                .orElseThrow(() -> new BookStoreNotFoundException(storeID));
    }

    @DeleteMapping("/bookstores/{storeID}")
    protected ResponseEntity<EntityModel<BookStore>> deleteBookStore(@PathVariable Long storeID) throws Exception{
        try{
            storeRepository.findById(storeID).orElseThrow(() -> new BookStoreNotFoundException(storeID));
            storeRepository.deleteById(storeID);
            this.map.remove(storeID);
            URL url = new URL("http://71.172.193.59:8080/hub/" + storeID);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("DELETE");
            con.setRequestProperty("Content-Type", "application/json");
            con.setDoOutput(true);
            con.connect();
            int x = con.getResponseCode();
            List<Book> books = bookRepository.findByStoreID(storeID);
            for (Book book : books) {
                bookRepository.delete(book);
            }
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        }catch (BookStoreNotFoundException e){
            if(this.map.containsKey(storeID)){
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }else{
                throw e;
            }
        }
    }

    @DeleteMapping("/bookstores/map")
    protected void deleteFromMap(@RequestBody String json){
        JsonObject jso = new JsonParser().parse(json).getAsJsonObject();
        Long id = jso.get("id").getAsLong();
        this.map.remove(id);
    }

    private EntityModel<BookStore> postToHub(BookStore bookStore) throws Exception {
        URL hubAddress = new URL("http://71.172.193.59:8080/hub");
        HttpURLConnection con = (HttpURLConnection) hubAddress.openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/json");
        con.setDoOutput(true);
        con.connect();
        DataOutputStream out = new DataOutputStream(con.getOutputStream());
        Gson gson = new Gson();
        JsonObject jso = new JsonObject();
        jso.addProperty("address", this.url + "/bookstores/");
        String str = gson.toJson(jso);
        out.writeBytes(str);
        int x = con.getResponseCode();
        out.flush();
        out.close();
        try(BufferedReader br = new BufferedReader(
                new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String responseLine = null;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
            bookStore.setServerId(Long.parseLong(response.toString()));
            this.id = bookStore.getServerId();
        }
        storeRepository.flush();
        return assembler.toModel(storeRepository.save(bookStore));
    }
}

//TODO Fix getAll method
//TODO! Speed up IntelliJ (Or get rid of it entirely)
