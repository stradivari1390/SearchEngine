package searchengine.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.repository.SiteRepository;
import searchengine.services.searchingService.SearchingService;

@RestController
@RequestMapping("/api")
public class SearchingController {

    private final SearchingService searchingService;
    @Autowired
    private SiteRepository siteRepository;

    public SearchingController(SearchingService searchingService) {
        this.searchingService = searchingService;
    }

    @GetMapping(value = "/search")
    public ResponseEntity<?> search(@RequestParam(name = "query", required = false) String query,
                                    @RequestParam(name = "site", required = false) String site,
                                    @RequestParam(name = "offset", defaultValue = "0") int offset,
                                    @RequestParam(name = "limit", defaultValue = "20") int limit) {
        if (query == null) {
            JSONObject response = new JSONObject();

            try {
                response.put("result", false);
                response.put("error", "Задан пустой поисковый запрос");
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
            return new ResponseEntity<>(response.toString(), HttpStatus.OK);
        }

        if (site != null && siteRepository.findByUrl(site).getUrl() == null) {

            JSONObject response = new JSONObject();

            try {
                response.put("result", false);
                response.put("error", "Указанная страница не найдена");
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
            return new ResponseEntity<>(response.toString(), HttpStatus.OK);
        }
        return new ResponseEntity<>(searchingService.search(query, site, offset, limit), HttpStatus.OK);
    }
}