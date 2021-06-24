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
public class BookController {

    private final BookRepository repository;
    private final BookModelAssembler assembler;
    private final BookStoreRepository storeRepository;

    public BookController(BookRepository repository, BookModelAssembler assembler, BookStoreRepository storeRepository) {
        this.repository = repository;
        this.assembler = assembler;
        this.storeRepository = storeRepository;
    }

    @PostMapping("/bookstores/{storeID}/books")
    protected ResponseEntity<EntityModel<Book>> newBook(@RequestBody Book book, @PathVariable Long storeID){
        BookStore store = storeRepository.getById(storeID);
        book.setStoreID(storeID);
        book.setStore(store);
        EntityModel<Book> entityModel = assembler.toModel(repository.save(book));

        return ResponseEntity
                .created(entityModel.getRequiredLink(IanaLinkRelations.SELF).toUri())
                .body(entityModel);
    }

    @GetMapping("/bookstores/{storeID}/books/{id}")
    protected EntityModel<Book> one(@PathVariable Long id, @PathVariable Long storeID) {
        Book book = repository.findById(id).orElseThrow(() -> new BookNotFoundException(id));

        return assembler.toModel(book);

    }

    @GetMapping("/bookstores/{storeID}/books")
    protected CollectionModel<EntityModel<Book>> all(@PathVariable Long storeID){
        List<EntityModel<Book>> booksAll = repository.findByStoreID(storeID)
                .stream()
                .map(assembler::toModel)
                .collect(Collectors.toList());
        return CollectionModel.of(booksAll, linkTo(methodOn(BookController.class).all(storeID)).withSelfRel());
    }

    @PutMapping("/bookstores/{storeID}/books/{id}")
    protected Book updateBook(@RequestBody Book newBook, @PathVariable Long id, @PathVariable Long storeID) {
        return repository.findById(id)
                .map(book -> {
                    if(newBook.getAuthor() != null) book.setAuthor(newBook.getAuthor());
                    if(newBook.getPrice() != -1)  book.setPrice(newBook.getPrice());
                    if(newBook.getCategory() != null) book.setCategory(newBook.getCategory());
                    if(newBook.getDescription() != null) book.setDescription(newBook.getDescription());
                    if(newBook.getLanguage() != null) book.setLanguage(newBook.getLanguage());
                    if(newBook.getTitle() != null) book.setTitle(newBook.getTitle());
                    return repository.save(book);
                })
                .orElseThrow(() -> new BookNotFoundException(id));
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/bookstores/{storeID}/books/{id}")
    protected void deleteBook(@PathVariable Long id, @PathVariable Long storeID) {
        repository.findById(id).orElseThrow(() -> new BookNotFoundException(id));
        repository.deleteById(id);
    }

}