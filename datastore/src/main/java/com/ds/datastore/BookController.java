package com.ds.datastore;

import static com.ds.datastore.Utilities.*;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import com.google.gson.*;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.http.HttpResponse;
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
    Logger logger = LoggerFactory.getLogger(BookController.class);

    public BookController(BookRepository repository, BookModelAssembler assembler, BookStoreRepository storeRepository, ServerMap map, Leader leader) {
        this.repository = repository;
        this.assembler = assembler;
        this.storeRepository = storeRepository;
        this.map = map;
        this.leader = leader;
    }

    @RateLimiter(name = "DDoS-stopper")
    @PostMapping("/bookstores/{storeID}/books")
    protected ResponseEntity<EntityModel<Book>> newBook(@RequestBody Book book, @PathVariable Long storeID) throws Exception{
        try{
            BookStore store = checkStore(storeID);
            book.setStoreID(storeID);
            book.setStore(store);
            EntityModel<Book> entityModel = assembler.toModel(repository.save(book));
            logger.info("Book {} by {} has been added", book.getTitle(), book.getAuthor());
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

    private boolean amILeader(){
        return this.storeRepository.findAll().get(0).getServerId().equals(this.leader.getLeader());
    }

    //Retry only worked when placed here, on the more global method but did not work on the Utilities method
    @RateLimiter(name = "DDoS-stopper")
    @Retry(name = "retry")
    @PostMapping("/bookstores/book")
    protected CollectionModel<EntityModel<Book>> oneBookToManyStores(@RequestBody Book book, @RequestParam List<String> id) throws Exception {
        List<EntityModel<Book>> entityList = new ArrayList<>();
        if (!amILeader()) {
            String address = this.map.get(this.leader.getLeader());
            address = removeIDNum(address) + "book?id=" + String.join(",", id);
            HttpResponse<String> response = createConnection(address, book.makeJson(), null, null, "POST");
            if(response.statusCode() != 200){
                logger.warn("Server {} was not reached", leader.getLeader());
                throw new RuntimeException("Could not connect to " + address);
            }
            JsonObject jso = new JsonParser().parse(response.body()).getAsJsonObject();
            JsonArray bookArray = jso.getAsJsonObject("_embedded").getAsJsonArray("bookList");
            for (JsonElement element: bookArray) {
                Book newBook = new Book(element.getAsJsonObject());
                entityList.add(assembler.toModel(newBook));
            }
            logger.info("Batch request successfully executed by {}", leader.getLeader());
        }else{
            for(String storeId : id) {
                book.setStoreID(Long.parseLong(storeId));
                if(!this.map.containsKey(Long.parseLong(storeId))) continue;
                String address = this.map.get(Long.parseLong(storeId)) + "/books";
                HttpResponse<String> response = createConnection(address, book.makeJson(), null, null, "POST");
                if(response.statusCode() != 201){
                    continue;
                }
                JsonParser parser = new JsonParser();
                JsonObject jo = parser.parse(response.body()).getAsJsonObject();
                entityList.add(assembler.toModel(new Book(jo)));
            }
            logger.info("Batch request successfully handled");
        }
        return CollectionModel.of(entityList, linkTo(methodOn(BookController.class).oneBookToManyStores(null, null)).withSelfRel());
    }

    //Retry only worked when placed here, on the more global method but did not work on the Utilities method
    @RateLimiter(name = "DDoS-stopper")
    @Retry(name = "retry")
    @PostMapping("/bookstores/books")
    protected CollectionModel<EntityModel<Book>> multipleToMultiple(@RequestBody BookArray json) throws Exception {
        if(!amILeader()){
            return multipleToLeader(json);
        }
        List<EntityModel<Book>> entityModelList = new ArrayList<>();
        for (Book book: json.getBooks()) {
            if(book.getStoreID() == null || !this.map.containsKey(book.getStoreID())) {
                continue;
            }
            JsonObject jso = book.makeJson();
            jso.addProperty("storeID", book.getStoreID());
            createConnection(this.map.get(book.getStoreID()) + "/books", jso, null, null, "POST");
            entityModelList.add(assembler.toModel(book));
        }
        logger.info("Batch of multiple to multiple completed");
        return CollectionModel.of(entityModelList, linkTo(methodOn(BookController.class)).withSelfRel());
    }

    private String removeIDNum(String address){
        return address.substring(0,address.lastIndexOf("/") + 1);
    }

    private CollectionModel<EntityModel<Book>> multipleToLeader(BookArray array) throws Exception {
        String address = this.map.get(leader.getLeader());
        address = removeIDNum(address) + "books";
        JsonArray jsonArray = new JsonArray();
        for (Book book : array.getBooks()) {
            JsonObject jso = book.makeJson();
            jso.addProperty("storeID", book.getStoreID());
            jsonArray.add(jso);
        }
        JsonObject elementedArray = new JsonObject();
        elementedArray.add("books", jsonArray);
        HttpResponse<String> response = createConnection(address, elementedArray, null, null, "POST");
        if(response.statusCode() != 200) throw new RuntimeException("Could not connect to " + address);
        JsonObject jso = new JsonParser().parse(response.body()).getAsJsonObject();
        JsonArray bookArray = jso.getAsJsonObject("_embedded").getAsJsonArray("bookList");
        ArrayList<EntityModel<Book>> entityModels = new ArrayList<>();
        for (JsonElement element: bookArray) {
            Book book = new Book(element.getAsJsonObject());
            entityModels.add(assembler.toModel(book));
        }
        logger.info("Batch of multiple to multiple completed by " + leader.getLeader());
        return  CollectionModel.of(entityModels, linkTo(methodOn(BookController.class)).withSelfRel());
    }

    @RateLimiter(name = "DDoS-stopper")
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

    @RateLimiter(name = "DDoS-stopper")
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

    @RateLimiter(name = "DDoS-stopper")
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
                    logger.info("Book updated");
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

    @RateLimiter(name = "DDoS-stopper")
    @DeleteMapping("/bookstores/{storeID}/books/{id}")
    protected ResponseEntity<EntityModel<Book>> deleteBook(@PathVariable Long id, @PathVariable Long storeID) throws Exception{
        try{
            checkStore(storeID);
            checkBook(id, storeID);
            repository.deleteById(id);
            logger.info("Book permanently terminated");
            return ResponseEntity.noContent().build();
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
