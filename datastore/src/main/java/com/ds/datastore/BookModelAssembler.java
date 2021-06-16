package com.ds.datastore;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.*;

import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

@Component
public class BookModelAssembler implements RepresentationModelAssembler<Book, EntityModel<Book>> {

    @Override
    public EntityModel<Book> toModel(Book book) {

        return EntityModel.of(book, //
                linkTo(methodOn(Datastore.class).one(book.getId())).withSelfRel(),
                linkTo(methodOn(Datastore.class).all()).withRel("employees"));
    }
}
