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

@RestController
public class BookStoreController {

    private final BookStoreRepository repository;

    private final BookStoreModelAssembler assembler;

    private HashMap<Long, BookController> map;



    public BookStoreController(BookStoreRepository repository, BookStoreModelAssembler assembler) {
        this.repository = repository;
        this.assembler = assembler;
        this.map = new HashMap<>();
    }

    @PostMapping("/bookstores")
    protected ResponseEntity<EntityModel<BookStore>> newBookStore(@RequestBody BookStore bookStore){
        EntityModel<BookStore> entityModel = assembler.toModel(repository.save(bookStore));

        return ResponseEntity
                .created(entityModel.getRequiredLink(IanaLinkRelations.SELF).toUri())
                .body(entityModel);
    }

   /* @PostMapping("/bookstores/{store_id}/books")
    protected ResponseEntity<EntityModel<Book>> newBook(@RequestBody Book book, @PathVariable Long store_id){
        BookStore bookStore = repository.findById(store_id).orElseThrow();
        BookModelAssembler bookModelAssembler = new BookModelAssembler();
        EntityModel<Book> entityModel = bookModelAssembler.toModel(book);
        bookStore.addBook(book);
        return ResponseEntity
                .created(entityModel.getRequiredLink(IanaLinkRelations.SELF).toUri())
                .body(entityModel);
    }
    */

    @GetMapping("/bookstores/{id}")
    protected EntityModel<BookStore> one(@PathVariable Long id) {
        BookStore book = repository.findById(id).orElseThrow(() -> new BookNotFoundException(id));


        return assembler.toModel(book);

    }


    @GetMapping("/bookstores")
    protected CollectionModel<EntityModel<BookStore>> all(){
        List<EntityModel<BookStore>> bookStores = repository.findAll().stream()
                .map(assembler::toModel)
                .collect(Collectors.toList());
        return CollectionModel.of(bookStores, linkTo(methodOn(BookStoreController.class).all()).withSelfRel());
    }

    @PutMapping("/bookstores/{id}")
    protected BookStore updateBook(@RequestBody BookStore newBookStore, @PathVariable Long id) {
        return repository.findById(id)
                .map(bookStore -> {

                    return repository.save(bookStore);
                })
                .orElseThrow(() -> new BookNotFoundException(id));
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/bookstores/{id}")
    protected void deleteBook(@PathVariable Long id) {
        repository.findById(id).orElseThrow(() -> new BookNotFoundException(id));
        repository.deleteById(id);
    }

}
