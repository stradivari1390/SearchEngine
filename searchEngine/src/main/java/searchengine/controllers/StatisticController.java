package searchengine.controllers;

import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import searchengine.services.StatisticsService;

@RestController
@RequestMapping("/api")
public class StatisticController {

    private final StatisticsService statisticsService;

    public StatisticController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping("/statistics")
    public ResponseEntity<JSONObject> statistics() {

        JSONObject response = new JSONObject();

        try {
                response.put("result", true);
                response.put("statistics", statisticsService.getStatistics().getStatisticsData());     //ToDo: finalize
            } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}