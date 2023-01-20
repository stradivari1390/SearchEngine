package searchengine.controllers;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.services.IndexingService;

@RestController
@RequestMapping("/api")
public class IndexingController {

    private final IndexingService indexingService;

    private static final Logger logger = LogManager.getLogger(IndexingController.class);

    public IndexingController(IndexingService indexingService) {
        this.indexingService = indexingService;
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<String> startIndexing() {
        logger.info("Received request to start indexing");
        boolean isIndexing = indexingService.startIndexing();
        JSONObject response = new JSONObject();

        try {
            if (isIndexing) {
                response.put("result", false);
                response.put("error", "Индексация уже запущена");
            } else {
                response.put("result", true);
            }
        } catch (JSONException e) {
            logger.error("Error: ", e);
            throw new RuntimeException(e);
        }
        logger.info(response.toString());
        return new ResponseEntity<>(response.toString(), HttpStatus.OK);
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<String> stopIndexing() {
        logger.info("Received request to stop indexing");
        boolean stopIndexing = indexingService.stopIndexing();
        JSONObject response = new JSONObject();

        try {
            if (stopIndexing) {
                response.put("result", true);
            } else {
                response.put("result", false);
                response.put("error", "Индексация не запущена");
            }
        } catch (JSONException e) {
            logger.error("Error: ", e);
            throw new RuntimeException(e);
        }
        logger.info(response.toString());
        return new ResponseEntity<>(response.toString(), HttpStatus.OK);
    }

    @PostMapping("/indexPage")
    public ResponseEntity<String> indexPage(@RequestParam(name = "url") String url) {
        logger.info("Received request to index page: {}", url);
        if (url == null) {
            return new ResponseEntity<>("", HttpStatus.BAD_REQUEST);
        }

        boolean addPage = indexingService.indexPage(url);
        JSONObject response = new JSONObject();

        try {
            if (addPage) {
                response.put("result", true);
            } else {
                response.put("result", false);
                response.put("error", "Данная страница находится за пределами сайтов, " +
                        "указанных в конфигурационном файле");
            }
        } catch (JSONException e) {
            logger.error("Error: ", e);
            throw new RuntimeException(e);
        }
        logger.info(response.toString());
        return new ResponseEntity<>(response.toString(), HttpStatus.OK);
    }
}