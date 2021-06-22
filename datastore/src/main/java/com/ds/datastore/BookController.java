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

    public BookController(BookRepository repository, BookModelAssembler assembler) {
        this.repository = repository;
        this.assembler = assembler;
    }

    @PostMapping("/books")
    protected ResponseEntity<EntityModel<Book>> newBook(@RequestBody Book book){
        EntityModel<Book> entityModel = assembler.toModel(repository.save(book));
        return ResponseEntity
                .created(entityModel.getRequiredLink(IanaLinkRelations.SELF).toUri())
                .body(entityModel);
    }
    
    @GetMapping("books/{id}")
    protected EntityModel<Book> one(@PathVariable Long id) {
        Book book = repository.findById(id).orElseThrow(() -> new BookNotFoundException(id));
        return assembler.toModel(book);
    }

    @GetMapping("/{store_id}/books")
    protected CollectionModel<EntityModel<Book>> all(){
        List<EntityModel<Book>> books = repository.findAll().stream()
                .map(assembler::toModel)
                .collect(Collectors.toList());
        return CollectionModel.of(books, linkTo(methodOn(BookController.class).all()).withSelfRel());
    }

    @PutMapping("/{store_id}/books/{id}")
    protected Book updateBook(@RequestBody Book newBook, @PathVariable Long id) {
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
    @DeleteMapping("/{store_id}/books/{id}")
    protected void deleteBook(@PathVariable Long id, @PathVariable Long store_id) {
        //TODO: do we need the store_id passed as a parameter?
        repository.findById(id).orElseThrow(() -> new BookNotFoundException(id));
        repository.deleteById(id);
    }

}
