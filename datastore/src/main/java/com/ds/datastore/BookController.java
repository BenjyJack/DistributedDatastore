package com.ds.datastore;


import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@RestController
public class BookController {

    private final BookRepository repository;

    private final BookModelAssembler assembler;

    BookController(BookRepository repository, BookModelAssembler assembler) {

        this.repository = repository;
        this.assembler = assembler;
    }

    @PostMapping("/books")
    Book newBook(@RequestBody Book book) {
        return repository.save(book);
    }
    @GetMapping("/books/{id}")
    EntityModel<Book> one(@PathVariable Long id) {
        Book book = repository.findById(id).orElseThrow(() -> new BookNotFoundException(id));
        return assembler.toModel(book);
    }
    @GetMapping("/books")
    CollectionModel<EntityModel<Book>> all(){

        List<EntityModel<Book>> books = repository.findAll().stream() //
                .map(assembler::toModel) //
                .collect(Collectors.toList());

        return CollectionModel.of(books, linkTo(methodOn(BookController.class).all()).withSelfRel());
    }

    @PutMapping("/books/{id}")
    Book updatePrice(@RequestBody Book newBook, @PathVariable Long id) {

        return repository.findById(id)
                .map(book -> {
                    book.setPrice(newBook.getPrice());
                    return repository.save(book);
                })
                .orElseThrow(() -> new BookNotFoundException(id));
    }
    @DeleteMapping("/books/{id}")
    void deleteBook(@PathVariable Long id) {
        repository.deleteById(id);
    }



//    public Book getBook(String serial)
//    {
//        return map.get(serial);
//    }
//
//    public void changePrice(String serial, double price)
//    {
//        Book book = map.get(serial);
//        book.setPrice(price);
//    }
//
//    public void deleteBook(String serial)
//    {
//        map.remove(serial);
//    }

}
