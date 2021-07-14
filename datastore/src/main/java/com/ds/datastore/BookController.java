package com.ds.datastore;

import static com.ds.datastore.Utilities.*;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RestController
public class BookController {

    private final BookRepository repository;
    private final BookModelAssembler assembler;
    private final BookStoreRepository storeRepository;
    private ServerMap map;
    private Leader leader;

    public BookController(BookRepository repository, BookModelAssembler assembler, BookStoreRepository storeRepository, ServerMap map, Leader leader) {
        this.repository = repository;
        this.assembler = assembler;
        this.storeRepository = storeRepository;
        this.map = map;
        this.leader = leader;
    }

    @PostMapping("/bookstores/{storeID}/books")
    protected ResponseEntity<EntityModel<Book>> newBook(@RequestBody Book book, @PathVariable Long storeID) throws Exception{
        try{
            BookStore store = checkStore(storeID);
            book.setStoreID(storeID);
            book.setStore(store);
            EntityModel<Book> entityModel = assembler.toModel(repository.save(book));
            return ResponseEntity
                .created(entityModel.getRequiredLink(IanaLinkRelations.SELF).toUri())
                .body(entityModel);
        }catch(BookStoreNotFoundException e){
            if (this.map.containsKey(storeID)) {
                return redirect(storeID);
            }else{
                throw e;
            }
        }     
    }

    @PostMapping("/bookstores/book")
    protected CollectionModel<EntityModel<Book>> oneBookToManyStores(@RequestBody Book book, @RequestParam List<String> id) throws Exception {
        List<EntityModel<Book>> entityList = new ArrayList<>();
        for(String storeId: id)
        {
            book.setStoreID(Long.parseLong(storeId));
            if(!this.map.containsKey(Long.parseLong(storeId))) continue;
            HttpURLConnection con = createConnection(this.map.get(Long.parseLong(storeId)) + "/books", "POST");
            Gson gson = new Gson();
            JsonObject jso = book.makeJson();
            outputJson(con, gson, jso);
            entityList.add(assembler.toModel(new Book(book)));
        }
        return CollectionModel.of(entityList, linkTo(methodOn(BookController.class).oneBookToManyStores(null, null)).withSelfRel());
    }

    @PostMapping("bookstores/books")
    protected CollectionModel<EntityModel<Book>> multipleToMultiple(@RequestBody BookArray json) throws Exception {
        if(!leader.getLeader().equals(storeRepository.findAll().get(0).getServerId())){
            return multipleToLeader(json);
        }
        List<EntityModel<Book>> entityModelList = new ArrayList<>();
        for (Book book: json.getBooks()) {
            if(book.getStoreID() == null || !this.map.containsKey(book.getStoreID())) {
                continue;
            }
            JsonObject jso = book.makeJson();
            jso.addProperty("storeID", book.getStoreID());
            HttpURLConnection con = createConnection(this.map.get(book.getStoreID()) + "/books", "POST");
            Gson gson = new Gson();
            outputJson(con, gson, jso);
            entityModelList.add(assembler.toModel(book));
        }
        return CollectionModel.of(entityModelList, linkTo(methodOn(BookController.class)).withSelfRel());
    }

    private CollectionModel<EntityModel<Book>> multipleToLeader(BookArray array) throws Exception {
        String address = new URL(this.map.get(leader.getLeader())).getHost();
        JsonArray jsonArray = new JsonArray();
        for (Book book : array.getBooks()) {
            JsonObject jso = book.makeJson();
            jso.addProperty("storeID", book.getStoreID());
            jsonArray.add(jso);
        }
        JsonObject elementedArray = new JsonObject();
        elementedArray.add("books", jsonArray);
        Gson gson = new Gson();
        String str = gson.toJson(elementedArray);
//        String str = "{\n" +
//                "    \"books\" :[\n" +
//                "        {\n" +
//                "            \"title\": \"still named\",\n" +
//                "            \"storeID\": 234234235\n" +
//                "        },\n" +
//                "        {\n" +
//                "            \"title\": \"not sure what I want to call this\",\n" +
//                "            \"storeID\": 101,\n" +
//                "            \"price\": 8.00\n" +
//                "        },\n" +
//                "        {\n" +
//                "            \"title\": \"another book\",\n" +
//                "            \"author\": \"definitely someone\"\n" +
//                "        },\n" +
//                "        {\n" +
//                "            \"title\" : \"booking-ness\",\n" +
//                "            \"author\" : \"Person McMann\",\n" +
//                "            \"storeID\": 101\n" +
//                "        },\n" +
//                "        {\n" +
//                "            \"title\": \"Yonatan's fun time of StoryTelling\",\n" +
//                "            \"author\" : \"Not Yonatan\",\n" +
//                "            \"storeID\": 135\n" +
//                "        },\n" +
//                "        {\n" +
//                "            \"title\":\"booking-ness\",\n" +
//                "            \"author\":\"Person McMann\",\n" +
//                "            \"storeID\": 102\n" +
//                "        }\n" +
//                "    ]\n" +
//                "}";
//        str = str.trim();
        HttpURLConnection con = createConnection("http://" + address + "/bookstores/books", "POST");
        try(OutputStream os = con.getOutputStream()) {
            byte[] input = str.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }
        int y = con.getResponseCode();
//        try(BufferedReader br = new BufferedReader(
//                new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
//            StringBuilder response = new StringBuilder();
//            String responseLine = null;
//            while ((responseLine = br.readLine()) != null) {
//                response.append(responseLine.trim());
//            }
//            System.out.println(response.toString());
//        }
        con.disconnect();
        return null;
    }

    @GetMapping("/bookstores/{storeID}/books/{bookId}")
    protected ResponseEntity<EntityModel<Book>> one(@PathVariable Long bookId, @PathVariable Long storeID) throws Exception{
        try{
            checkStore(storeID);
            Book book = checkBook(bookId, storeID);
            EntityModel<Book> entityModel = assembler.toModel(book);
            return ResponseEntity.created(entityModel.getRequiredLink(IanaLinkRelations.SELF).toUri())
            .body(entityModel);
        }catch(BookStoreNotFoundException e){
            if (this.map.containsKey(storeID)) {
                return redirectWithId(bookId, storeID);
            }else{
                throw e;
            }
        }
    }

    @GetMapping("/bookstores/{storeID}/books")
    protected ResponseEntity<CollectionModel<EntityModel<Book>>> all(@PathVariable Long storeID, @RequestParam(required = false) List<String> id) throws Exception{
        List<EntityModel<Book>> booksAll = null;
        try {
            checkStore(storeID);
            if(id != null) {
                return getAllSpecific(storeID, id);
            }
            booksAll = repository.findByStoreID(storeID)
                .stream()
                .map(assembler::toModel)
                .collect(Collectors.toList());
            return ResponseEntity.ok(CollectionModel.of(booksAll, linkTo(methodOn(BookController.class).all(storeID, null)).withSelfRel()));
        }catch (BookStoreNotFoundException e) {
            if (this.map.containsKey(storeID)) {
                UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(this.map.get(storeID) + "/books");
                URI uri = new URI(builder.toUriString());
                return ResponseEntity.status(HttpStatus.PERMANENT_REDIRECT).location(uri).build();
            }else{
                throw e;
            }
        }     
    }

    private ResponseEntity<CollectionModel<EntityModel<Book>>> getAllSpecific(Long storeID, List<String> id) throws Exception {
        List<EntityModel<Book>> entModelList = new ArrayList<>();
        for(String bookId : id) {
            Long parsedId = Long.parseLong(bookId);
            if(repository.findById(parsedId).isEmpty()) {
                continue;
            }
            entModelList.add(assembler.toModel(repository.findById(parsedId).get()));
        }
        return ResponseEntity.ok(CollectionModel.of(entModelList, linkTo(methodOn(BookController.class).all(storeID, null)).withSelfRel()));
    }

    @PutMapping("/bookstores/{storeID}/books/{id}")
    protected ResponseEntity<EntityModel<Book>> updateBook(@RequestBody Book newBook, @PathVariable Long id, @PathVariable Long storeID) throws Exception{
        try{
            checkStore(storeID);
            Book updatedBook = repository.findById(id)
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
            EntityModel<Book> entityModel = assembler.toModel(updatedBook);
            return ResponseEntity.created(entityModel.getRequiredLink(IanaLinkRelations.SELF).toUri()).body(entityModel);
        }catch(BookStoreNotFoundException e){
            if (this.map.containsKey(storeID)) {
                return redirectWithId(id, storeID);
            }else{
                throw e;
            }
        }
    }

    @DeleteMapping("/bookstores/{storeID}/books/{id}")
    protected ResponseEntity<EntityModel<Book>> deleteBook(@PathVariable Long id, @PathVariable Long storeID) throws Exception{
        try{
            checkStore(storeID);
            checkBook(id, storeID);
            repository.deleteById(id);
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        }catch(BookStoreNotFoundException e){
            if (this.map.containsKey(storeID)) {
                return redirectWithId(id, storeID);
            }else{
                throw e;
            }
        }
    }

    private BookStore checkStore(Long storeID){
        return storeRepository.findById(storeID).orElseThrow(() -> new BookStoreNotFoundException(storeID));
    }

    private Book checkBook(Long id, Long storeID) {
        return repository.findById(id).orElseThrow(() -> new BookNotFoundException(id));
    }

    private ResponseEntity<EntityModel<Book>> redirect(Long storeId) throws Exception{
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(this.map.get(storeId) + "/books");
        URI uri = new URI(builder.toUriString());
        return ResponseEntity.status(HttpStatus.PERMANENT_REDIRECT).location(uri).build();
    }

    private ResponseEntity<EntityModel<Book>> redirectWithId(Long bookId, Long storeId) throws Exception{
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(this.map.get(storeId) + "/books/" + bookId);
        URI uri = new URI(builder.toUriString());
        return ResponseEntity.status(HttpStatus.PERMANENT_REDIRECT).location(uri).build();
    }
}
