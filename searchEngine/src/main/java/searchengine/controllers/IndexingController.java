package searchengine.controllers;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import searchengine.responses.Response;
import searchengine.services.IndexingService;

@RestController
@RequestMapping("/api")
public class IndexingController {

    private final IndexingService indexingService;

    private static final Logger logger = LogManager.getLogger(IndexingController.class);

    public IndexingController(IndexingService indexingService) {
        this.indexingService = indexingService;
    }

    @Transactional
    @GetMapping("/startIndexing")
    public ResponseEntity<JSONObject> startIndexing() {
        logger.info("Received request to start indexing");
        Response startIndexingResponse = indexingService.startIndexing();
        logger.info(startIndexingResponse.toString());
        return new ResponseEntity<>(startIndexingResponse.get(), startIndexingResponse.getHttpStatus());
    }

    @Transactional
    @GetMapping("/stopIndexing")
    public ResponseEntity<JSONObject> stopIndexing() {
        logger.info("Received request to stop indexing");
        Response stopIndexingResponse = indexingService.stopIndexing();
        logger.info(stopIndexingResponse.toString());
        return new ResponseEntity<>(stopIndexingResponse.get(), stopIndexingResponse.getHttpStatus());
    }

    @Transactional
    @PostMapping("/indexPage")
    public ResponseEntity<JSONObject> indexPage(@RequestParam(name = "url") String url) {
        logger.info("Received request to index a page: {}", url);
        Response indexPageResponse = indexingService.indexPage(url);
        logger.info(indexPageResponse.toString());
        return new ResponseEntity<>(indexPageResponse.get(), indexPageResponse.getHttpStatus());
    }
}