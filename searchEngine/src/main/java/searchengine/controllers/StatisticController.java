package searchengine.controllers;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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

    private static final Logger logger = LogManager.getLogger(StatisticController.class);

    public StatisticController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping("/statistics")
    public ResponseEntity<JSONObject> statistics() {
        logger.info("Received request to get statistics");
        JSONObject response = new JSONObject();

        try {
                response.put("result", true);
                response.put("statistics", statisticsService.getStatistics().getStatisticsData());     //ToDo: finalize
            } catch (JSONException e) {
            logger.error("Error retrieving statistics", e);
            throw new RuntimeException(e);
        }
        logger.info(response.toString());
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}