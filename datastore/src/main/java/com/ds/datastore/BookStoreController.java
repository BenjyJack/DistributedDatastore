package com.ds.datastore;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.annotation.PostConstruct;

import com.google.gson.*;
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
import static com.ds.datastore.Utilities.*;

@RestController
public class BookStoreController {

    private final BookModelAssembler bookModelAssembler;
    private final BookStoreModelAssembler bookStoreModelAssembler;
    private final BookRepository bookRepository;
    private final BookStoreRepository storeRepository;
    private ServerMap map;
    private Long id = null;
    private Leader leader;

    @Value("${application.baseUrl}")
    private String url;

    @Value("${hub.url}")
    private String hubUrl;

    public BookStoreController(BookStoreModelAssembler bookStoreModelAssembler, BookModelAssembler bookModelAssembler, BookRepository bookRepository, BookStoreRepository storeRepository, ServerMap map, Leader leader) {
        this.bookStoreModelAssembler = bookStoreModelAssembler;
        this.bookModelAssembler = bookModelAssembler;
        this.bookRepository = bookRepository;
        this.storeRepository = storeRepository;
        this.map = map;
        this.leader = leader;
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
                registerWithHub();
            }
        }
        this.leader.setLeader(getLeader());
    }

    private void registerWithHub() throws Exception {
        HttpURLConnection con = createConnection(hubUrl, "PUT");
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
        con.disconnect();
    }

    private HashMap<Long, String> reclaimMap() throws Exception {
        HttpURLConnection con = createConnection(hubUrl);
        InputStream inStream = con.getInputStream();
        JsonReader reader = new JsonReader(new InputStreamReader(inStream, StandardCharsets.UTF_8));
        Gson gson = new Gson();
        Type type = new TypeToken<HashMap<Long, String>>(){}.getType();
        HashMap<Long, String> map = gson.fromJson(reader, type);
        inStream.close();
        reader.close();
        con.disconnect();
        return map;
    }

    private Long getLeader() throws Exception {
        HttpURLConnection con = createGetConnection(hubUrl + "/leader");
        DataInputStream inStream = (DataInputStream) con.getInputStream();
        Long leader = inStream.readLong();
        inStream.close();
        con.disconnect();
        return leader;
    }

    @PutMapping("/bookstores/{storeID}/leader")
    protected void setLeader(@RequestBody Long leader)
    {
        this.leader.setLeader(leader);
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
            EntityModel<BookStore> entityModel = bookStoreModelAssembler.toModel(bookStore);
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
    protected CollectionModel<EntityModel<BookStore>> getBookStores(@RequestParam(required = false) List<String> id) throws Exception {
        List<EntityModel<BookStore>> entModelList = new ArrayList<>();
        if(id == null) {
            for (Long storeId : this.map.keySet()) {
                try{
                    entModelList.add(getAndParseBookStore(this.map.get(storeId)));
                }catch(Exception e){
                    continue;
                }
            }
        }else{
            for(String storeID : id) {
                Long parsedId = Long.parseLong(storeID);
                String address = this.map.get(parsedId);
                if(address == null) {
                    continue;
                }
                try{
                    entModelList.add(getAndParseBookStore(address));
                }catch(Exception e){
                    continue;
                }
            }
        }
        return CollectionModel.of(entModelList, linkTo(methodOn(BookStoreController.class).getBookStores(null)).withSelfRel());
    }

    private EntityModel<BookStore> getAndParseBookStore(String address) throws Exception{
        HttpURLConnection con = createConnection(address);
        JsonObject jso = getJsonObject(con);

        BookStore store = new BookStore();
        store.setServerId((jso.get("serverId") != null ? jso.get("serverId").getAsLong() : null));
        store.setName(!jso.get("name").isJsonNull() ? jso.get("name").getAsString() : null);
        store.setPhone(!jso.get("phone").isJsonNull() ? jso.get("phone").getAsString() : null);
        store.setStreetAddress(!jso.get("streetAddress").isJsonNull() ? jso.get("streetAddress").getAsString() : null);
        //Not including the List of books contained in the store
        return bookStoreModelAssembler.toModel(store);
    }

    @GetMapping("/bookstores/books")
    protected CollectionModel<EntityModel<Book>> getAllBooksFromBookStores(@RequestParam (required = false) List<String> id) throws Exception {
        if(id == null) {
            List<String> arrayList = new ArrayList<>();
            for(Long storeID : this.map.keySet()) {
                arrayList.add(String.valueOf(storeID));
            }
            id = arrayList;
        }
        List<EntityModel<Book>> entModelList = new ArrayList<>();
        for(String storeID : id) {
            Long parsedId = Long.parseLong(storeID);
            String address = this.map.get(parsedId);
            if(address == null) {
                continue;
            }
            HttpURLConnection con = createConnection(address);
            JsonObject jso = null;
            try{
                jso = getJsonObject(con);
            }catch(Exception e){
                continue;
            }
            Gson gson = new Gson();
            Type collectionType = new TypeToken<List<Book>>(){}.getType();
            List<Book> books = gson.fromJson(jso.get("books"), collectionType);
            for(Book book : books) {
                entModelList.add(bookModelAssembler.toModel(book));
            }
        }
        return CollectionModel.of(entModelList, linkTo(methodOn(BookStoreController.class).getAllBooksFromBookStores(null)).withSelfRel());
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
            HttpURLConnection con = createConnection(hubUrl + "/" + storeID, "DELETE");
            con.getResponseCode();
            List<Book> books = bookRepository.findByStoreID(storeID);
            for (Book book : books) {
                bookRepository.delete(book);
            }
            con.disconnect();
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
    @GetMapping("/bookstores/{storeID}/ping")
    protected boolean ping()
    {
        return true;
    }

    private EntityModel<BookStore> postToHub(BookStore bookStore) throws Exception {
        HttpURLConnection con = createConnection(hubUrl, "POST");
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
        con.disconnect();
        return bookStoreModelAssembler.toModel(storeRepository.save(bookStore));
    }

    // For GET requests
    private HttpURLConnection createConnection(String address) throws Exception {
        URL url = new URL(address);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        con.setRequestProperty("accept", "application/json");
        con.setDoOutput(true);
        con.connect();
        int x = con.getResponseCode();
        return con;
    }

    // For POST, PUT, and DELETE requests
    private HttpURLConnection createConnection(String address, String request) throws Exception {
        URL url = new URL(address);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod(request);
        con.setRequestProperty("Content-Type", "application/json");
        con.setDoOutput(true);
        con.connect();
        return con;
    }

    private JsonObject getJsonObject(HttpURLConnection con) throws IOException {
        InputStream inStream = con.getInputStream();
        JsonObject jso = (JsonObject) new JsonParser().parse(new InputStreamReader(inStream, StandardCharsets.UTF_8));
        inStream.close();
        con.disconnect();
        return jso;
    }

}

