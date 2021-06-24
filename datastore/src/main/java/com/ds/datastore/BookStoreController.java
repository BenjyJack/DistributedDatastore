package com.ds.datastore;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import com.google.gson.Gson;

@RestController
public class BookStoreController {

    private final BookStoreRepository repository;
    private final BookStoreModelAssembler assembler;
    private final BookRepository bookRepository;
    private HashMap<Long,String> serverMap;

    public BookStoreController(BookStoreRepository repository, BookStoreModelAssembler assembler, BookRepository bookRepository) {
        this.repository = repository;
        this.assembler = assembler;
        this.bookRepository = bookRepository;
        this.serverMap = new HashMap<>();
    }

    @PostMapping("/bookstores")
    protected ResponseEntity<EntityModel<BookStore>> newBookStore(@RequestBody BookStore bookStore){
        EntityModel<BookStore> entityModel = assembler.toModel(repository.save(bookStore));

        return ResponseEntity
                .created(entityModel.getRequiredLink(IanaLinkRelations.SELF).toUri())
                .body(entityModel);
    }

    //TODO Clean Up these three methods
    @PostMapping("/bookstores/{id}")
    protected void addServer(Long storeID, String address){
        this.serverMap.put(storeID, address);
    }

    @PostMapping("/bookstores/{id}")
    protected void setMap(@RequestBody String map)
    {
        Gson gson = new Gson();
        this.serverMap = (HashMap<Long,String>)gson.fromJson(map, HashMap.class);
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

    @GetMapping("")









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
