package searchengine.controllers;

import lombok.SneakyThrows;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.bind.annotation.*;

import searchengine.dto.responses.ErrorResponse;
import searchengine.dto.responses.Response;
import searchengine.dto.responses.SearchResponse;
import searchengine.services.SearchingService;

@RestController
@RequestMapping("/api")
public class SearchingController {

    private final SearchingService searchingService;

    @Autowired
    public SearchingController(SearchingService searchingService) {
        this.searchingService = searchingService;
    }

    @SneakyThrows
    @GetMapping(value = "/search")
    public ResponseEntity<?> search(@RequestParam(name = "query", required = false) String query,
                                    @RequestParam(name = "site", required = false) String site,
                                    @RequestParam(name = "offset", defaultValue = "0") int offset,
                                    @RequestParam(name = "limit", defaultValue = "10") int limit) {
        Response response = searchingService.search(query, site, offset, limit);
        if (response instanceof ErrorResponse) {
            ErrorResponse errorResponse = (ErrorResponse) response;
            return new ResponseEntity<>(errorResponse.get(), errorResponse.getHttpStatus());
        } else {
            SearchResponse searchResponse = (SearchResponse) response;
            return new ResponseEntity<>(searchResponse, HttpStatus.OK);
        }
    }

    @ResponseBody
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public String handleHttpMediaTypeNotAcceptableException() {
        return "Search query should not be empty";
    }
}