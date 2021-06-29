package com.ds.datastore;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import com.google.gson.reflect.TypeToken;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.http.HttpRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.HttpRequestHandler;
import org.springframework.web.bind.annotation.*;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.annotation.PostConstruct;

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
        this.repository=repository;
        this.serverMap = new HashMap<>();

    }
    private HashMap<Long, String> reclaimMap() throws Exception{
        URL url = new URL("http://71.187.80.134:8080/hub");
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        con.setRequestProperty("accept", "application/json");
        con.setDoOutput(true);
        InputStream in = con.getInputStream();

        JsonParser parser = new JsonParser();
        JsonObject jsonObject = (JsonObject) parser.parse(new InputStreamReader(in, "UTF-8"));

        HashMap<Long, String> map = new Gson().fromJson(jsonObject, new TypeToken<HashMap<Long, String>>() {}.getType());

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
                return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(serverMap.get(storeID))).build();
            }else{
                throw new BookStoreNotFoundException(storeID);
            }
        }

    }

    @GetMapping("/bookstores")
    protected CollectionModel<EntityModel<BookStore>> all(){
        List<EntityModel<BookStore>> bookStores = repository.findAll().stream()
                .map(assembler::toModel)
                .collect(Collectors.toList());
        return CollectionModel.of(bookStores, linkTo(methodOn(BookStoreController.class).all()).withSelfRel());
    }

    @PutMapping("/bookstores/{storeID}")
    protected BookStore updateBookStore(@RequestBody BookStore newBookStore, @PathVariable Long storeID) {
        return repository.findById(storeID)
                .map(bookStore -> {
                    if(newBookStore.getName() != null) bookStore.setName(newBookStore.getName());
                    if(newBookStore.getPhone() != null) bookStore.setPhone(newBookStore.getPhone());
                    if(newBookStore.getAddress() != null) bookStore.setAddress(newBookStore.getAddress());
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
        jso.addProperty("address", String.valueOf(url));
        String str = gson.toJson(jso);
        out.writeBytes(str);
        int x = con.getResponseCode();
        out.flush();
        out.close();

        return entityModel;

    }

}
