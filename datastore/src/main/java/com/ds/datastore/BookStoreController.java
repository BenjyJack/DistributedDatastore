package com.ds.datastore;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
public class BookStoreController {

    private final BookStoreRepository repository;
    private final BookStoreModelAssembler assembler;
    private final BookRepository bookRepository;
    private HashMap<Long, String> serverMap;
    @Value("${application.baseUrl}")
    private String url;

    @PostConstruct
    private void restartChangedOrNew() throws Exception {
        if(!repository.findAll().isEmpty()) {
            this.serverMap = reclaimMap();
            if(!serverMap.get(repository.findAll().get(0).getId()).equals(url))
            {
                postToHub(repository.findAll().get(0));
            }
        }
    }

    public BookStoreController(BookStoreRepository repository, BookStoreModelAssembler assembler, BookRepository bookRepository) throws Exception{
        this.assembler = assembler;
        this.bookRepository = bookRepository;
        this.repository = repository;
        this.serverMap = new HashMap<>();
    }
    
    private HashMap<Long, String> reclaimMap() throws Exception{
        URL url = new URL("http://71.187.80.134:8080/hub");
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        con.setRequestProperty("accept", "application/json");
        con.setDoOutput(true);
        con.connect();
        InputStream instream = con.getInputStream();
        JsonReader reader = new JsonReader(new InputStreamReader(instream, StandardCharsets.UTF_8));
        Gson gson = new Gson();
        Type type = new TypeToken<HashMap<Long, String>>(){}.getType();
        HashMap<Long, String> map = gson.fromJson(reader, type);
        instream.close();
        reader.close();
        return map;
    }

    @PostMapping("/bookstores")
    protected ResponseEntity<EntityModel<BookStore>> newBookStore(@RequestBody BookStore bookStore) throws Exception{
        EntityModel<BookStore> entityModel = postToHub(bookStore);
        return ResponseEntity
                .created(entityModel.getRequiredLink(IanaLinkRelations.SELF).toUri())
                .body(entityModel);
    }

    @PostMapping("/bookstores/{id}")
    protected void addServer(@RequestBody String json, @PathVariable Long id){
        JsonObject jso = new JsonParser().parse(json).getAsJsonObject();
        Long givenID = jso.getAsJsonObject().get("id").getAsLong();
        String address = jso.getAsJsonObject().get("address").getAsString();
        BookStore store = this.repository.findById(id).get();
        this.serverMap.put(givenID, address);
    }

    @GetMapping("/bookstores/{storeID}")
    protected ResponseEntity one(@PathVariable Long storeID) {
        BookStore bookStore = null;
        try{
            bookStore = repository.findById(storeID).orElseThrow(() -> new BookStoreNotFoundException(storeID));
            EntityModel<BookStore> entityModel = assembler.toModel(repository.save(bookStore));
            return ResponseEntity
                    .created(entityModel.getRequiredLink(IanaLinkRelations.SELF).toUri())
                    .body(entityModel);
        }catch (BookStoreNotFoundException e){
            if(serverMap.containsKey(storeID)){
                UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(this.serverMap.get(storeID));
                URI uri = builder.path("/bookstores/{storeId}").buildAndExpand(storeID).toUri();
                return ResponseEntity.status(HttpStatus.PERMANENT_REDIRECT).location(uri).build();
            }else{
                throw new BookStoreNotFoundException(storeID);
            }
        }
    }

    @GetMapping("/bookstores")
    protected CollectionModel<EntityModel<BookStore>> all() throws Exception {
        List<EntityModel<BookStore>> entModelList = new ArrayList<>();
        for (Long id : this.serverMap.keySet()) {
            ResponseEntity<BookStore> rpe = one(id);
            BookStore bStore = rpe.getBody();
            EntityModel<BookStore> em = assembler.toModel(bStore);
            entModelList.add(em);
        }
        return CollectionModel.of(entModelList, linkTo(methodOn(BookStoreController.class).all()).withSelfRel());
    }




//        for(Long id : serverMap.keySet()) {
//            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(this.serverMap.get(id));
//            String uri = builder.path("/bookstores/" + id).buildAndExpand(id).toString();
//            URL url = new URL(uri);
//            HttpURLConnection con = (HttpURLConnection) url.openConnection();
//            con.setRequestMethod("GET");
//            con.setRequestProperty("accept", "application/json");
//            con.setDoOutput(true);
//            con.connect();
//            InputStream instream = con.getInputStream();
//            System.out.println(instream);
//            JsonReader reader = new JsonReader(new InputStreamReader(instream, StandardCharsets.UTF_8));
//            JsonObject jso = new JsonParser().parse(reader).getAsJsonObject();
//            System.out.println(jso);
//            instream.close();
//            reader.close();
//            jsonObjects.add(jso);
//        }
//        List<EntityModel<BookStore>> bookStores = repository.findAll().stream()
//                .map(assembler::toModel)
//                .collect(Collectors.toList());
//        return CollectionModel.of(bookStores, linkTo(methodOn(BookStoreController.class).all()).withSelfRel());

//    }

    @PutMapping("/bookstores/{storeID}")
    protected BookStore updateBookStore(@RequestBody BookStore newBookStore, @PathVariable Long storeID) {
        return repository.findById(storeID)
                .map(bookStore -> {
                    if(newBookStore.getName() != null) bookStore.setName(newBookStore.getName());
                    if(newBookStore.getPhone() != null) bookStore.setPhone(newBookStore.getPhone());
                    if(newBookStore.getStreetAddress() != null) bookStore.setStreetAddress(newBookStore.getStreetAddress());
                    return repository.save(bookStore);
                })
                .orElseThrow(() -> new BookStoreNotFoundException(storeID));
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/bookstores/{storeID}")
    protected void deleteBookStore(@PathVariable Long storeID) {
        repository.findById(storeID).orElseThrow(() -> new BookStoreNotFoundException(storeID));
        repository.deleteById(storeID);
        List<Book> books = bookRepository.findByStoreID(storeID);
        for (Book book : books) {
            bookRepository.delete(book);
        }
    }

    private EntityModel<BookStore> postToHub(BookStore bookStore) throws Exception {
        EntityModel<BookStore> entityModel = assembler.toModel(repository.save(bookStore));
        URL url = new URL("http://71.187.80.134:8080/hub");
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/json");
        con.setDoOutput(true);
        DataOutputStream out = new DataOutputStream(con.getOutputStream());
        Gson gson = new Gson();
        JsonObject jso = new JsonObject();
        jso.addProperty("id", bookStore.getId());
        jso.addProperty("address", String.valueOf(this.url));
        String str = gson.toJson(jso);
        out.writeBytes(str);
        out.flush();
        out.close();
        return entityModel;
    }
}
