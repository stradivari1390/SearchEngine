package searchengine.controllers;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.responses.Response;
import searchengine.services.SearchingService;

@RestController
@RequestMapping("/api")
public class SearchingController {

    @Autowired
    private SearchingService searchingService;

    private static final Logger logger = LogManager.getLogger(SearchingController.class);

    public SearchingController(SearchingService searchingService) {
        this.searchingService = searchingService;
    }

    @GetMapping(value = "/search")
    public ResponseEntity<JSONObject> search(@RequestParam(name = "query", required = false) String query,
                                             @RequestParam(name = "site", required = false) String site,
                                             @RequestParam(name = "offset", defaultValue = "0") int offset,
                                             @RequestParam(name = "limit", defaultValue = "20") int limit) {
        logger.info("Received request to search: " + query);
        Response searchResponse = searchingService.search(query, site, offset, limit);
        logger.info(searchResponse.toString());
        return searchResponse.get();
    }
}