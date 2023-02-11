package searchengine.controllers;

import lombok.SneakyThrows;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.responses.Response;
import searchengine.services.IndexingService;

@RestController
@RequestMapping("/api")
public class IndexingController {

    private final IndexingService indexingService;

    private final Logger logger = LogManager.getLogger(IndexingController.class);

    @Autowired
    public IndexingController(IndexingService indexingService) {
        this.indexingService = indexingService;
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<Response> startIndexing() {
        logger.info("Received request to start indexing");
        Response startIndexingResponse = indexingService.startIndexing();
        logger.info("Indexing started: {}", startIndexingResponse);
        return new ResponseEntity<>(startIndexingResponse, startIndexingResponse.getHttpStatus());
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<Response> stopIndexing() {
        logger.info("Received request to stop indexing");
        Response stopIndexingResponse = indexingService.stopIndexing();
        logger.info("Indexing stopped: {}", stopIndexingResponse);
        return new ResponseEntity<>(stopIndexingResponse, stopIndexingResponse.getHttpStatus());
    }

    @SneakyThrows
    @PostMapping("/indexPage")
    public ResponseEntity<Response> indexPage(@RequestParam(name = "url") String url) {
        logger.info("Received request to index a page: {}", url);
        Response indexPageResponse = indexingService.indexPage(url);
        logger.info("Page: {}, -- indexed: {}", url, indexPageResponse);
        return new ResponseEntity<>(indexPageResponse, indexPageResponse.getHttpStatus());
    }
}