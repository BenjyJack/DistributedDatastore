package com.ds.datastore;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.io.DataOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

@RestController
public class BookStoreController {

    private final BookStoreRepository repository;
    private final BookStoreModelAssembler assembler;
    private final BookRepository bookRepository;
    private HashMap<Long,String> serverMap;

    public BookStoreController(BookStoreRepository repository, BookStoreModelAssembler assembler, BookRepository bookRepository) throws Exception{
        this.repository = repository;
        this.assembler = assembler;
        this.bookRepository = bookRepository;
        this.serverMap = new HashMap<>();
    }

    @PostMapping("/bookstores")
    protected ResponseEntity<EntityModel<BookStore>> newBookStore(@RequestBody BookStore bookStore) throws Exception{
        EntityModel<BookStore> entityModel = assembler.toModel(repository.save(bookStore));
        URL url = new URL("http://localhost:8080/hub");
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/json");
        con.setDoOutput(true);
        DataOutputStream out = new DataOutputStream(con.getOutputStream());
        Gson gson = new Gson();
        JsonObject jso = new JsonObject();
        jso.addProperty("id", bookStore.getId());
        jso.addProperty("address", "http://localhost:8081/bookstores/" + bookStore.getId());
        String str = gson.toJson(jso);
        out.writeBytes(str);
        int x = con.getResponseCode();
        out.flush();
        out.close();
        return ResponseEntity
                .created(entityModel.getRequiredLink(IanaLinkRelations.SELF).toUri())
                .body(entityModel);
    }

    @PostMapping("/bookstores/{id}")
    protected void addServer(@RequestBody String json, @PathVariable Long id){
        JsonObject jso = new JsonParser().parse(json).getAsJsonObject();
        Long givenID = jso.getAsJsonObject().get("id").getAsLong();
        String address = jso.getAsJsonObject().get("address").getAsString();
        this.serverMap.put(givenID, address);
    }

    @GetMapping("/bookstores/{storeID}")
    protected EntityModel<BookStore> one(@PathVariable Long storeID) {
        BookStore bookStore = repository.findById(storeID).orElseThrow(() -> new BookStoreNotFoundException(storeID));
        return assembler.toModel(bookStore);
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

}
