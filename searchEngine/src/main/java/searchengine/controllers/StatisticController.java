package searchengine.controllers;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.configurationprocessor.json.JSONArray;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.StatisticsService;

import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("/api")
public class StatisticController {

    private final StatisticsService statisticsService;

    private static final Logger logger = LogManager.getLogger(StatisticController.class);

    public StatisticController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping("/statistics")
    @ResponseBody
    public ResponseEntity<StatisticsResponse> statistics() {
        logger.info("Received request to get statistics");
//        JSONObject response = new JSONObject();
        StatisticsResponse statisticsResponse = statisticsService.getStatistics();
//        StatisticsData statisticsData = statisticsResponse.getStatisticsData();
//        if (statisticsData != null) {
//            List<DetailedStatisticsItem> detailedStatistics = statisticsData.getDetailed();
//            try {
//                response.put("result", true);
//                JSONArray detailedStatisticsArray = new JSONArray();
//                for (DetailedStatisticsItem item : detailedStatistics) {
//                    JSONObject itemJSON = new JSONObject();
//                    itemJSON.put("url", item.getUrl());
//                    itemJSON.put("name", item.getName());
//                    itemJSON.put("status", item.getStatus());
//                    itemJSON.put("statusTime", new Date(item.getStatusTime()));
//                    itemJSON.put("error", item.getError());
//                    itemJSON.put("pages", item.getPages());
//                    itemJSON.put("lemmas", item.getLemmas());
//                    detailedStatisticsArray.put(itemJSON);
//                }
//                response.put("statistics", detailedStatisticsArray);
//
//            } catch (JSONException e) {
//                logger.error("Error retrieving statistics", e);
//                throw new RuntimeException(e);
//            }
//        } else {
//            try {
//                response.put("result", false);
//                response.put("error", "No statistics to collect");
//            } catch (JSONException e) {
//                throw new RuntimeException(e);
//            }
//        }
        logger.info(statisticsResponse.toString());
        return new ResponseEntity<>(statisticsResponse, HttpStatus.OK);
    }
}