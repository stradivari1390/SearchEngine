package searchengine.controllers;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import searchengine.responses.Response;
import searchengine.services.StatisticsService;

@RestController
@RequestMapping("/api")
public class StatisticController {

    @Autowired
    private final StatisticsService statisticsService;

    private static final Logger logger = LogManager.getLogger(StatisticController.class);

    public StatisticController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @Transactional
    @GetMapping("/statistics")
    public ResponseEntity<JSONObject> statistics() {
        logger.info("Received request to get statistics");
        Response statisticsResponse = statisticsService.getStatistics();
        logger.info(statisticsResponse);
        return new ResponseEntity<>(statisticsResponse.get(), statisticsResponse.getHttpStatus());
    }
}