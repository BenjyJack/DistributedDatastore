package com.ds.datastore;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
public class BookStoreController {

    private final BookStoreRepository repository;
    private final BookStoreModelAssembler assembler;

    public BookStoreController(BookStoreRepository repository, BookStoreModelAssembler assembler) {
        this.repository = repository;
        this.assembler = assembler;
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

    @GetMapping("/bookstores/{store_id}")
    protected EntityModel<BookStore> one(@PathVariable Long store_id) {
        BookStore bookStore = repository.findById(store_id).orElseThrow(() -> new BookStoreNotFoundException(store_id));
        return assembler.toModel(bookStore);
    }


    @GetMapping("/bookstores")
    protected CollectionModel<EntityModel<BookStore>> all(){
        List<EntityModel<BookStore>> bookStores = repository.findAll().stream()
                .map(assembler::toModel)
                .collect(Collectors.toList());
        return CollectionModel.of(bookStores, linkTo(methodOn(BookStoreController.class).all()).withSelfRel());
    }

    @PutMapping("/bookstores/{store_id}")
    protected BookStore updateBookStore(@RequestBody BookStore newBookStore, @PathVariable Long store_id) {
        return repository.findById(store_id)
                .map(bookStore -> {
                    //TODO: add more stuff here
                    return repository.save(bookStore);
                })
                .orElseThrow(() -> new BookStoreNotFoundException(store_id));
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/bookstores/{store_id}")
    protected void deleteBookStore(@PathVariable Long store_id) {
        repository.findById(store_id).orElseThrow(() -> new BookStoreNotFoundException(store_id));
        repository.deleteById(store_id);
    }

}
