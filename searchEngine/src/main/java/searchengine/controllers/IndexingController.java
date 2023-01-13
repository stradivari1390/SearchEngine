package searchengine.controllers;

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

        public IndexingController(IndexingService indexingService) {
            this.indexingService = indexingService;
        }

        @GetMapping("/startIndexing")
        public ResponseEntity<String> startIndexing() {
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
                throw new RuntimeException(e);
            }
            return new ResponseEntity<>(response.toString(), HttpStatus.OK);
        }

        @GetMapping("/stopIndexing")
        public ResponseEntity<String> stopIndexing() {
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
                throw new RuntimeException(e);
            }
            return new ResponseEntity<>(response.toString(), HttpStatus.OK);
        }

        @PostMapping("/indexPage")
        public ResponseEntity<String> indexPage(@RequestParam(name = "url") String url) {
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
                throw new RuntimeException(e);
            }
            return new ResponseEntity<>(response.toString(), HttpStatus.OK);
        }
}