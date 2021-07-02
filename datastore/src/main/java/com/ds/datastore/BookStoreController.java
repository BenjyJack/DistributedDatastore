package com.ds.datastore;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
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

    private HashMap<Long, String> serverMap;

    @Value("${application.baseUrl}")
    private String url;

    public BookStoreController(BookStoreModelAssembler assembler, BookRepository bookRepository, BookStoreRepository storeRepository) {
        this.assembler = assembler;
        this.bookRepository = bookRepository;
        this.storeRepository = storeRepository;
    }

    @PostConstruct
    private void restartChangedOrNew() throws Exception {
        this.serverMap = reclaimMap();
        List<BookStore> bookStoreList = storeRepository.findAll();
        if(!bookStoreList.isEmpty()) {
            BookStore bookStore = bookStoreList.get(0);
            Long serverId = bookStore.getServerId();
            String serverAddress = serverMap.get(serverId);
            if(!serverAddress.equals(url)) {
                postToHub(bookStore);
            }
        }
    }

    private HashMap<Long, String> reclaimMap() throws Exception {
        URL url = new URL("http://71.187.80.134:8080/hub");
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
        EntityModel<BookStore> entityModel = postToHub(bookStore);
        return ResponseEntity
                .created(entityModel.getRequiredLink(IanaLinkRelations.SELF).toUri())
                .body(entityModel);
    }

    @PostMapping("/bookstores/{id}")
    protected void addServer(@RequestBody String json, @PathVariable String id) {
        JsonObject jso = new JsonParser().parse(json).getAsJsonObject();
        Long givenID = jso.get("id").getAsLong();
        String address = jso.get("address").getAsString();
        Long idL = Long.parseLong(id);

        if(idL.equals(givenID)){
            BookStore store = this.storeRepository.findAll().get(0);
            store.setServerId(givenID);
            assembler.toModel(storeRepository.saveAndFlush(store));
        }

        this.serverMap.put(givenID, address);
    }

    @GetMapping("/bookstores/{storeID}")
    protected ResponseEntity one(@PathVariable Long storeID) throws Exception {
        try{
            BookStore bookStore = storeRepository.findByServerId(storeID).orElseThrow(() -> new BookStoreNotFoundException(storeID));
            EntityModel<BookStore> entityModel = assembler.toModel(storeRepository.save(bookStore));
            return ResponseEntity
                    .created(entityModel.getRequiredLink(IanaLinkRelations.SELF).toUri())
                    .body(entityModel);
        }catch (BookStoreNotFoundException e){
            if(serverMap.containsKey(storeID)){
                UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(this.serverMap.get(storeID));
                URI uri = new URI(builder.toUriString());
                return ResponseEntity.status(HttpStatus.PERMANENT_REDIRECT).location(uri).build();


                /*URI uri = new URI(this.serverMap.get(storeID));
                return ResponseEntity.status(HttpStatus.PERMANENT_REDIRECT).location(uri).build();*/
            }else{
                throw e;
            }
        }
    }

    @GetMapping("/bookstores")
    protected CollectionModel<EntityModel<BookStore>> all() throws Exception {
        List<EntityModel<BookStore>> entModelList = new ArrayList<>();
        for (Long id : this.serverMap.keySet()) {
            ResponseEntity<BookStore> rpe = one(id);
            BookStore bookStore = rpe.getBody();
            EntityModel<BookStore> em = assembler.toModel(bookStore);
            entModelList.add(em);
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

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/bookstores/{storeID}")
    protected void deleteBookStore(@PathVariable Long storeID) {
        storeRepository.findById(storeID).orElseThrow(() -> new BookStoreNotFoundException(storeID));
        storeRepository.deleteById(storeID);
        List<Book> books = bookRepository.findByStoreID(storeID);
        for (Book book : books) {
            bookRepository.delete(book);
        }
    }

    private EntityModel<BookStore> postToHub(BookStore bookStore) throws Exception {
        EntityModel<BookStore> store = assembler.toModel(storeRepository.save(bookStore));
        URL hubAddress = new URL("http://71.187.80.134:8080/hub");
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
            storeRepository.findAll().get(0).setServerId(Long.parseLong(response.toString()));
        }
        storeRepository.flush();
        return assembler.toModel(storeRepository.findAll().get(0));
    }

}
