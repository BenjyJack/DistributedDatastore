package com.ds.datastore;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import java.net.URI;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;

import javax.servlet.http.HttpServletRequest;

@RestController
public class BookController {

    private final BookRepository repository;
    private final BookModelAssembler assembler;
    private final BookStoreRepository storeRepository;
    private ServerMap map;
    private Leader leader;
    Logger logger = LoggerFactory.getLogger(BookController.class);
    private Utilities utilities;

    public BookController(BookRepository repository, BookModelAssembler assembler, BookStoreRepository storeRepository, ServerMap map, Leader leader, Utilities utilities) {
        this.repository = repository;
        this.assembler = assembler;
        this.storeRepository = storeRepository;
        this.map = map;
        this.leader = leader;
        this.utilities = utilities;
    }

    @RateLimiter(name = "DDoS-stopper")
    @PostMapping("/bookstores/{storeID}/books")
    protected ResponseEntity<EntityModel<Book>> newBook(@RequestBody Book book, @PathVariable Long storeID, HttpServletRequest request) throws Exception{
        String orderID = request.getAttribute("orderID").toString();
        // if(request.getHeader("orderID") == null)
        // {
        //     orderID = String.valueOf(UUID.randomUUID());
        //     request.setAttribute("orderID", orderID);
        // }else{
        //     orderID = request.getHeader("orderID");
        // }
        // logger.info("Received request {}", orderID);
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

    @RateLimiter(name = "DDoS-stopper")
    @PostMapping("/bookstores/book")
    protected CollectionModel<EntityModel<Book>> oneBookToManyStores(@RequestBody Book book, @RequestParam List<String> id, HttpServletRequest request) throws Exception {
        String orderID = request.getAttribute("orderID").toString();
        // if(request.getHeader("orderID") == null)
        // {
        //     orderID = String.valueOf(UUID.randomUUID());
        //     request.setAttribute("orderID", orderID);
        // }else{
        //     orderID = request.getHeader("orderID");
        // }
        // logger.info("Request {} received", orderID);
        List<EntityModel<Book>> entityList = new ArrayList<>();
        if (!amILeader()) {
            String address = this.map.get(this.leader.getLeader());
            address = removeIDNum(address) + "book?id=" + String.join(",", id);
            HttpResponse<String> response = (utilities.createConnection(address, book.makeJson(), null, null, "POST", orderID));
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
            logger.info("Leader handling request {}", request.getHeader("orderID"));
            for(String storeId : id) {
                book.setStoreID(Long.parseLong(storeId));
                if(!this.map.containsKey(Long.parseLong(storeId))) continue;
                String address = this.map.get(Long.parseLong(storeId)) + "/books";
                Optional<HttpResponse<String>> optional = utilities.createConnectionCircuitBreaker(address, book.makeJson(), null, null, "POST", orderID);
                if(optional.isEmpty()){
                    continue;
                }
                HttpResponse<String> response = optional.get();
                JsonParser parser = new JsonParser();
                JsonObject jo = parser.parse(response.body()).getAsJsonObject();
                entityList.add(assembler.toModel(new Book(jo)));
            }
            logger.info("Batch request successfully handled");
        }
        return CollectionModel.of(entityList, linkTo(methodOn(BookController.class).oneBookToManyStores(null, null, null)).withSelfRel());
    }

    @RateLimiter(name = "DDoS-stopper")
    @PostMapping("/bookstores/books")
    protected CollectionModel<EntityModel<Book>> multipleToMultiple(@RequestBody BookArray json, HttpServletRequest request) throws Exception {
        String orderID = request.getAttribute("orderID").toString();
        // if(request.getHeader("orderID") == null)
        // {
        //     orderID = String.valueOf(UUID.randomUUID());
        //     request.setAttribute("orderID", orderID);
        // }else{
        //     orderID = request.getHeader("orderID");
        // }
        // logger.info("Request {} received", orderID);
        if(!amILeader()){
            return multipleToLeader(json, orderID);
        }
        List<EntityModel<Book>> entityModelList = new ArrayList<>();
        for (Book book: json.getBooks()) {
            if(book.getStoreID() == null || !this.map.containsKey(book.getStoreID())) {
                continue;
            }
            JsonObject jso = book.makeJson();
            jso.addProperty("storeID", book.getStoreID());
            Optional<HttpResponse<String>> optional = utilities.createConnectionCircuitBreaker(this.map.get(book.getStoreID()) + "/books", jso, null, null, "POST", orderID);
            if(!optional.isEmpty()) {
                entityModelList.add(assembler.toModel(book));
            }
        }
        logger.info("Batch of multiple to multiple completed");
        return CollectionModel.of(entityModelList, linkTo(methodOn(BookController.class)).withSelfRel());
    }

    private String removeIDNum(String address){
        return address.substring(0,address.lastIndexOf("/") + 1);
    }

    private CollectionModel<EntityModel<Book>> multipleToLeader(BookArray array, String orderID) throws Exception {
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
        HttpResponse<String> response = utilities.createConnection(address, elementedArray, null, null, "POST", orderID);
        logger.info("Request {} forwarded to leader", orderID);
        if(response.statusCode() != 200){
            logger.warn("{} status code received", response.statusCode());
            throw new RuntimeException("Could not connect to " + address);
        }
        logger.info("Books sent successfully");
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
    protected ResponseEntity<EntityModel<Book>> one(@PathVariable Long bookId, @PathVariable Long storeID, HttpServletRequest request) throws Exception{
        String orderID = request.getAttribute("orderID").toString();
        //logger.info("Request {} received", orderID);
        try{
            checkStore(storeID);
            Book book = checkBook(bookId);
            EntityModel<Book> entityModel = assembler.toModel(book);
            logger.info("Request {} handled", orderID);
            return ResponseEntity.created(entityModel.getRequiredLink(IanaLinkRelations.SELF).toUri())
            .body(entityModel);
        }catch(BookStoreNotFoundException e){
            if (this.map.containsKey(storeID)) {
                logger.info("Redirecting request {}", orderID);
                return redirectWithId(bookId, storeID);
            }else{
                logger.error("Book store not found", e);
                throw e;
            }
        }
    }

    @RateLimiter(name = "DDoS-stopper")
    @GetMapping("/bookstores/{storeID}/books")
    protected ResponseEntity<CollectionModel<EntityModel<Book>>> all(@PathVariable Long storeID, @RequestParam(required = false) List<String> id, HttpServletRequest request) throws Exception{
        String orderID = request.getAttribute("orderID").toString();
        //logger.info("Request {} recevied", orderID);
        List<EntityModel<Book>> booksAll;
        try {
            checkStore(storeID);
            if(id != null) {
                return getAllSpecific(storeID, id, orderID);
            }
            booksAll = repository.findByStoreID(storeID)
                .stream()
                .map(assembler::toModel)
                .collect(Collectors.toList());
            logger.info("Request {} successfully handled", orderID);
            return ResponseEntity.ok(CollectionModel.of(booksAll, linkTo(methodOn(BookController.class).all(storeID, null, null)).withSelfRel()));
        }catch (BookStoreNotFoundException e) {
            if (this.map.containsKey(storeID)) {
                UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(this.map.get(storeID) + "/books");
                URI uri = new URI(builder.toUriString());
                logger.info("Redirecting request {}", orderID);
                return ResponseEntity.status(HttpStatus.PERMANENT_REDIRECT).location(uri).header("orderID", orderID).build();
            }else{
                logger.warn("Bookstore not found", e);
                throw e;
            }
        }     
    }

    private ResponseEntity<CollectionModel<EntityModel<Book>>> getAllSpecific(Long storeID, List<String> id, String orderID) throws Exception {
        List<EntityModel<Book>> entModelList = new ArrayList<>();
        for(String bookId : id) {
            Long parsedId = Long.parseLong(bookId);
            if(repository.findById(parsedId).isEmpty()) {
                continue;
            }
            entModelList.add(assembler.toModel(repository.findById(parsedId).get()));
        }
        logger.info("Request {} successfully handled", orderID);
        return ResponseEntity.ok(CollectionModel.of(entModelList, linkTo(methodOn(BookController.class).all(storeID, null, null)).withSelfRel()));
    }

    @RateLimiter(name = "DDoS-stopper")
    @PutMapping("/bookstores/{storeID}/books/{id}")
    protected ResponseEntity<EntityModel<Book>> updateBook(@RequestBody Book newBook, @PathVariable Long id, @PathVariable Long storeID, HttpServletRequest request) throws Exception{
        String orderID = request.getAttribute("orderID").toString();
        //logger.info("Request {} received", orderID);
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
                    logger.info("Book {} updated", id);
                    return repository.save(book);
                })
                .orElseThrow(() -> new BookNotFoundException(id));
            EntityModel<Book> entityModel = assembler.toModel(updatedBook);
            return ResponseEntity.created(entityModel.getRequiredLink(IanaLinkRelations.SELF).toUri()).body(entityModel);
        }catch(BookStoreNotFoundException e){
            if (this.map.containsKey(storeID)) {
                logger.info("Redirecting request {}", orderID);
                return redirectWithId(id, storeID);
            }else{
                logger.warn("Bookstore not found", e);
                throw e;
            }
        }
    }

    @RateLimiter(name = "DDoS-stopper")
    @DeleteMapping("/bookstores/{storeID}/books/{id}")
    protected ResponseEntity<EntityModel<Book>> deleteBook(@PathVariable Long id, @PathVariable Long storeID, HttpServletRequest request) throws Exception{
        String orderID = request.getAttribute("orderID").toString();
        //logger.info("Request {} recevied", orderID);
        try{
            checkStore(storeID);
            checkBook(id);
            repository.deleteById(id);
            logger.info("Book {} permanently terminated", id);
            return ResponseEntity.noContent().build();
        }catch(BookStoreNotFoundException e){
            if (this.map.containsKey(storeID)) {
                logger.info("Redirecting request {}", orderID);
                return redirectWithId(id, storeID);
            }else{
                logger.warn("Bookstore not found", e);
                throw e;
            }
        }
    }

    private BookStore checkStore(Long storeID){
        return storeRepository.findById(storeID).orElseThrow(() -> new BookStoreNotFoundException(storeID));
    }

    private Book checkBook(Long id) {
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
