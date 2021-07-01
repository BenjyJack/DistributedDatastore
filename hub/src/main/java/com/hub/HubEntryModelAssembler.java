package com.hub;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

@Component
public class HubEntryModelAssembler implements RepresentationModelAssembler<HubEntry, EntityModel<HubEntry>> {
    @Override
    public EntityModel<HubEntry> toModel(HubEntry book) {
        return EntityModel.of(book, (Iterable<Link>) linkTo(methodOn(HubController.class).getMap()));
    }
}
