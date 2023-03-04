package searchengine.controllers;

import lombok.SneakyThrows;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import searchengine.dto.responses.Response;
import searchengine.services.IndexingService;

@RestController
@RequestMapping("/api")
public class IndexingController {

    private final IndexingService indexingService;

    @Autowired
    public IndexingController(IndexingService indexingService) {
        this.indexingService = indexingService;
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<Response> startIndexing() {
        Response startIndexingResponse = indexingService.startIndexing();
        return new ResponseEntity<>(startIndexingResponse, startIndexingResponse.getHttpStatus());
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<Response> stopIndexing() {
        Response stopIndexingResponse = indexingService.stopIndexing();
        return new ResponseEntity<>(stopIndexingResponse, stopIndexingResponse.getHttpStatus());
    }

    @SneakyThrows
    @PostMapping("/indexPage")
    public ResponseEntity<Response> indexPage(@RequestParam(name = "url") String url) {
        Response indexPageResponse = indexingService.indexPage(url);
        return new ResponseEntity<>(indexPageResponse, indexPageResponse.getHttpStatus());
    }
}