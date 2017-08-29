package com.CCGA.api.Controllers;

import com.CCGA.api.Models.Book;
import com.CCGA.api.Models.JSONResponse;
import com.CCGA.api.Models.User;
import com.CCGA.api.Repositorys.BookRepo;
import com.CCGA.api.Repositorys.UserRepo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import static org.springframework.http.HttpStatus.*;

@RestController
@RequestMapping("/api/book")
public class BookController {

    @Autowired
    BookRepo books;
    @Autowired
    UserRepo users;

    @GetMapping(value = "/all")
    public ResponseEntity getAllBooksOwned(HttpSession session) {
        try {
            List<Book> bookList = users.findOne((Integer) session.getAttribute("userID")).getBooksOwned();
            if (bookList == null) {
                return ResponseEntity.status(OK).body(new JSONResponse("User has no books", null));
            } else {
                return ResponseEntity.status(OK).body(new JSONResponse("Success", bookList));
            }
        } catch (Exception e) {
            return ResponseEntity.status(INTERNAL_SERVER_ERROR).body("Error fetching books");
        }
    }

    @PostMapping(value = "/owned/add", consumes = "application/json")
    public ResponseEntity addABookToCollection(@RequestBody String bookToBeAdded, HttpSession session) throws IOException {
        if (session.getAttribute("userID") != null) {
            JsonNode json;

            try {
                json = processJSON(bookToBeAdded);
            } catch (Exception e) {
                return ResponseEntity.status(BAD_REQUEST).body("Error processing request, please try again");
            }

            User loggedIn = users.findOne((Integer) session.getAttribute("userID"));
            Book added = books.findByIsbn(json.get("isbn").asText());
            loggedIn.addBook(added);

            users.save(loggedIn);

            return ResponseEntity.status(CREATED).header("Content-Type", "application/json").body(new JSONResponse("Book added to collection", added));
        } else {
            return ResponseEntity.status(UNAUTHORIZED).body("You must be logged in to add a book to your collection.");
        }
    }

    @PostMapping("/owned/add")
    public ResponseEntity createListingNotJSON(HttpSession session) {
        if (session.getAttribute("userID") != null) {
            return ResponseEntity.status(UNSUPPORTED_MEDIA_TYPE).body("Content-Type not supported, please use \"application/json\"");
        } else {
            return ResponseEntity.status(UNAUTHORIZED).body("You must be logged in to do that");
        }
    }

    @GetMapping("/owned/{bookID}")
    public ResponseEntity getSpecificBook(@PathVariable int bookID, HttpSession session) {
        try {
            User loggedIn = users.findOne((int) session.getAttribute("userID"));
            Book bookSearchedFor = books.findOne(bookID);
            List<Book> bookList = loggedIn.getBooksOwned();
            if (bookList.contains(bookSearchedFor)) {
                return ResponseEntity.status(OK).body(new JSONResponse("Success", bookSearchedFor));
            } else {
                return ResponseEntity.status(OK).body(new JSONResponse("No book with that ID found in user's collection", null));
            }
        } catch (Exception e) {
            return ResponseEntity.status(INTERNAL_SERVER_ERROR).body(new JSONResponse("Error", null));
        }
    }

    @DeleteMapping("/owned/{bookID}")
    public ResponseEntity deleteABook(@PathVariable int bookID, HttpSession session) {
        try {
            User loggedIn = users.findOne((int) session.getAttribute("userID"));
            Book book = books.findOne(bookID);

            if (loggedIn.getBooksOwned().contains(book)) {

                loggedIn.removeBookOwned(book);
                users.save(loggedIn);

                return ResponseEntity.status(NO_CONTENT).build();
            } else {
                return ResponseEntity.status(NOT_FOUND).body("User does not have specified book in collection");
            }
        } catch (Exception e) {
            return ResponseEntity.status(UNAUTHORIZED).body("You must be logged in to delete a book from your collection");
        }
    }

    @PostMapping("/search")
    public ResponseEntity bookSearch(@RequestBody String bookSearchJSON, HttpSession session) {
        if (session.getAttribute("userID") != null) {

            JsonNode json;

            try {
                json = processJSON(bookSearchJSON);
            } catch (Exception e) {
                return ResponseEntity.status(BAD_REQUEST).body("Error processing request, please try again");
            }

            if (json.get("isbn") != null) {
                Book book = books.findByIsbn(json.get("isbn").asText());
                if (book == null) {
                    // TODO: 8/23/17 Probably change this to specify people can/should submit missing book info
                    return ResponseEntity.status(OK).body("Book does not exist in our server");
                } else {
                    return ResponseEntity.status(OK).body(new JSONResponse("Book Found", book));
                }

            } else if (json.get("name") != null) {
                Book book = books.findByName(json.get("name").asText());
                if (book == null) {
                    // TODO: 8/23/17 Probably change this to specify people can/should submit missing book info
                    return ResponseEntity.status(OK).body("Book does not exist in our server");
                } else {
                    return ResponseEntity.status(OK).body(new JSONResponse("Book Found", book));
                }
            } else if (json.get("author") != null) {
                List<Book> booksFoundByAuthor = books.findAllByAuthor(json.get("author").asText());
                if (booksFoundByAuthor != null) {
                    // TODO: 8/23/17 Probably change this to specify people can/should submit missing book info
                    return ResponseEntity.status(OK).body("No Books found by that author found");
                }
            }
            return ResponseEntity.status(BAD_REQUEST).body("Please provide an ISBN or name to search for");
        } else {
            return ResponseEntity.status(UNAUTHORIZED).body("You must be logged in to do that");
        }
    }

    @GetMapping("/search/{isbn}")
    public ResponseEntity searchBookByISBN(@PathVariable String isbn, HttpSession session) {
        if (session.getAttribute("userID") != null) {

            Book book = books.findByIsbn(isbn);
            if (book == null) {
                // TODO: 8/23/17 Probably change this to specify people should submit missing book info
                return ResponseEntity.status(NOT_FOUND).body("Book does not exist in our server");
            } else {
                return ResponseEntity.status(OK).body(new JSONResponse("Book Found", book));
            }
        } else {
            return ResponseEntity.status(UNAUTHORIZED).body("You must be logged in to do that");
        }
    }


    private JsonNode processJSON(String toBeProcessed) throws Exception {
        JsonNode json = new ObjectMapper().readTree(new StringReader(toBeProcessed));
        if (json == null) {
            throw new Exception();
        }

        return json;
    }
}
