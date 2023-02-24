package searchengine.controllers;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import searchengine.dto.responses.StatisticsResponse;
import searchengine.services.StatisticsService;

@RestController
@RequestMapping("/api")
public class StatisticController {

    private final StatisticsService statisticsService;

    private static final Logger logger = LogManager.getLogger(StatisticController.class);

    @Autowired
    public StatisticController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }
    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        logger.info("Received request to get statistics");
        StatisticsResponse statisticsResponse = statisticsService.getStatistics();
        logger.info(statisticsResponse.toString());
        return new ResponseEntity<>(statisticsResponse, HttpStatus.OK);
    }
}