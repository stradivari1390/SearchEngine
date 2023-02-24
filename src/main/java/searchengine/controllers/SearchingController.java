package searchengine.controllers;


import lombok.SneakyThrows;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.responses.ErrorResponse;
import searchengine.dto.responses.Response;
import searchengine.dto.responses.SearchResponse;
import searchengine.services.SearchingService;

@RestController
@RequestMapping("/api")
public class SearchingController {
    private final SearchingService searchingService;
    private static final Logger logger = LogManager.getLogger(SearchingController.class);
    @Autowired
    public SearchingController(SearchingService searchingService) {
        this.searchingService = searchingService;
    }
    @SneakyThrows
    @GetMapping(value = "/search")
    public ResponseEntity<?> search(@RequestParam(name = "query", required = false) String query,
                                    @RequestParam(name = "site", required = false) String site,
                                    @RequestParam(name = "offset", defaultValue = "0") int offset,
                                    @RequestParam(name = "limit", defaultValue = "20") int limit) {
        logger.info("Received request to search: {}", query);
        Response response = searchingService.search(query, site, offset, limit);
        if (response instanceof ErrorResponse) {
            ErrorResponse errorResponse = (ErrorResponse) response;
            return new ResponseEntity<>(errorResponse.get(), errorResponse.getHttpStatus());
        } else {
            SearchResponse searchResponse = (SearchResponse) response;
            return new ResponseEntity<>(searchResponse, HttpStatus.OK);
        }
    }
}