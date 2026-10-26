package it.unipi.lsmsd.controller;

import it.unipi.lsmsd.model.ExtremeWeatherEventCategory;
import it.unipi.lsmsd.model.MeasurementField;
import it.unipi.lsmsd.service.AnalyticsService;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;


@RestController
@RequestMapping("/analytics")
public class AnalyticsController {

    @Autowired
    private AnalyticsService analyticsService;

    @GetMapping("/measurement")
    public ResponseEntity<Object> getMeasurements(
            @RequestParam String cityId,
            @RequestParam MeasurementField measurementField,
            @RequestParam LocalDateTime startDate,
            @RequestParam LocalDateTime endDate
    ) {
        List<Document> response = analyticsService.getMeasurementsList(cityId, measurementField, startDate, endDate);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    // <editor-fold desc="Measurements analytics with single city as target [ measurement/city/ ]">

    @GetMapping("/measurement/city/average")
    public ResponseEntity<Object> averageMeasurementInCityDuringPeriod(
            @RequestParam String cityId,
            @RequestParam MeasurementField measurementField,
            @RequestParam LocalDateTime startDate,
            @RequestParam LocalDateTime endDate
    ) {
        List<Document> response = analyticsService.averageMeasurementInCityDuringPeriod(cityId, measurementField, startDate, endDate);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @GetMapping("/measurement/city/average-per-month")
    public ResponseEntity<Object> averageMeasurementGroupByMonthInCityDuringPeriod(
            @RequestParam String cityId,
            @RequestParam MeasurementField measurementField,
            @RequestParam LocalDateTime startDate,
            @RequestParam LocalDateTime endDate
    ) {
        List<Document> response = analyticsService.averageMeasurementGroupByMonthInCityDuringPeriod(cityId, measurementField, startDate, endDate);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @GetMapping("/measurement/city/highest")
    public ResponseEntity<Object> highestMeasurementsInCityDuringPeriod(
            @RequestParam String cityId,
            @RequestParam MeasurementField measurementField,
            @RequestParam LocalDateTime startDate,
            @RequestParam LocalDateTime endDate,
            @RequestParam int numMeasurementsToFind
    ) {
        List<Document> response = analyticsService.highestMeasurementsInCityDuringPeriod(cityId, measurementField, startDate, endDate, numMeasurementsToFind);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @GetMapping("/measurement/city/lowest")
    public ResponseEntity<Object> lowestMeasurementsInCityDuringPeriod(
            @RequestParam String cityId,
            @RequestParam MeasurementField measurementField,
            @RequestParam LocalDateTime startDate,
            @RequestParam LocalDateTime endDate,
            @RequestParam int numMeasurementsToFind
    ) {
        List<Document> response = analyticsService.lowestMeasurementsInCityDuringPeriod(cityId, measurementField, startDate, endDate, numMeasurementsToFind);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    // </editor-fold>

    // <editor-fold desc="Measurements analytics across multiple cities [ measurement/ ]">

    @GetMapping("/measurement/region/average")
    public ResponseEntity<Object> highestAverageMeasurementAcrossCities(
            @RequestParam String region,
            @RequestParam MeasurementField measurementField,
            @RequestParam LocalDateTime startDate,
            @RequestParam LocalDateTime endDate
    ){
        List<Document> response = analyticsService.highestAverageMeasurementInRegion(measurementField, startDate, endDate, region);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
    @GetMapping("/measurement/region/highest")
    public ResponseEntity<Object> highestMeasurementPerCity(
            @RequestParam String region,
            @RequestParam MeasurementField measurementField,
            @RequestParam LocalDateTime startDate,
            @RequestParam LocalDateTime endDate
    ){
        List<Document> response = analyticsService.highestMeasurementInRegion(measurementField, startDate, endDate, region);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
    @GetMapping("/measurement/region/lowest")
    public ResponseEntity<Object> lowestMeasurementPerCity(
            @RequestParam String region,
            @RequestParam MeasurementField measurementField,
            @RequestParam LocalDateTime startDate,
            @RequestParam LocalDateTime endDate
    ){
        List<Document> response = analyticsService.lowestMeasurementInRegion(measurementField, startDate, endDate, region);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
    // </editor-fold>

    // <editor-fold desc="Measurements analytics of recent period [ measurement/recent/ ]">

    @GetMapping("/measurement/recent/city/average-per-day")
    public ResponseEntity<Object> averageMeasurementPerDayOfCity(
            @RequestParam MeasurementField measurementField,
            @RequestParam String cityId,
            @RequestParam int pastDays
    ) {
        List<Document> serviceResponse = analyticsService.recentAverageMeasurementPerDayOfCity(measurementField, cityId, pastDays);
        return ResponseEntity.status(HttpStatus.OK).body(serviceResponse);
    }

    @GetMapping("/measurement/recent/city/total-per-day")
    public ResponseEntity<Object> totalMeasurementPerDayOfCity(
            @RequestParam MeasurementField measurementField,
            @RequestParam String cityId,
            @RequestParam int pastDays
    ) {
        List<Document> serviceResponse = analyticsService.recentTotalMeasurementPerDayOfCity(measurementField, cityId, pastDays);
        return ResponseEntity.status(HttpStatus.OK).body(serviceResponse);
    }

    @GetMapping("/measurement/recent/region/average")
    public ResponseEntity<Object> averageMeasurementOfLastDaysOfRegion(
            @RequestParam MeasurementField measurementField,
            @RequestParam String region,
            @RequestParam int pastDays
    ) {
        List<Document> serviceResponse = analyticsService.getAverageMeasurementOfLastDaysOfRegion(measurementField, region, pastDays);
        return ResponseEntity.status(HttpStatus.OK).body(serviceResponse);
    }

    @GetMapping("/measurement/recent/region/total")
    public ResponseEntity<Object> totalMeasurementsOfLastDaysOfRegion(
            @RequestParam MeasurementField measurementField,
            @RequestParam String region,
            @RequestParam int pastDays
    ) {
        List<Document> serviceResponse = analyticsService.getTotalMeasurementLastDaysOfRegion(measurementField, region, pastDays);
        return ResponseEntity.status(HttpStatus.OK).body(serviceResponse);
    }

    // </editor-fold>



    // <editor-fold desc="Extreme Weather Event analytics in region [ ewe/ ]">

    @GetMapping("/ewe/count")
    public ResponseEntity<Object> citiesMostAffectedByEweInTimeRange(
            @RequestParam ExtremeWeatherEventCategory extremeWeatherEventCategory,
            @RequestParam String cityId,
            @RequestParam LocalDateTime startDate,
            @RequestParam LocalDateTime endDate
    ) {
        List<Document> serviceResponse = analyticsService.citiesMostAffectedByEweInTimeRange(extremeWeatherEventCategory, startDate, endDate, cityId);
        return ResponseEntity.status(HttpStatus.OK).body(serviceResponse);
    }

    @GetMapping("/ewe/count-of-at-least-strength")
    public ResponseEntity<Object> numberOfEweOfStrengthInTimeRange(
            @RequestParam ExtremeWeatherEventCategory extremeWeatherEventCategory,
            @RequestParam String cityId,
            @RequestParam int minimumStrength,
            @RequestParam LocalDateTime startDate,
            @RequestParam LocalDateTime end
    ) {
        List<Document> serviceResponse = analyticsService.numberOfEweOfStrengthInTimeRange(minimumStrength, extremeWeatherEventCategory, startDate, end, cityId);
        return ResponseEntity.status(HttpStatus.OK).body(serviceResponse);
    }

    @GetMapping("/ewe/count-per-month")
    public ResponseEntity<Object> eweCountMonthlyAverage(
            @RequestParam String cityId,
            @RequestParam ExtremeWeatherEventCategory extremeWeatherEventCategory,
            @RequestParam LocalDateTime startDate,
            @RequestParam LocalDateTime endDate
    ){
        List<Document> serviceResponse = analyticsService.eweCountByMonth(cityId, extremeWeatherEventCategory, startDate, endDate);
        return ResponseEntity.status(HttpStatus.OK).body(serviceResponse);
    }

    @GetMapping("/ewe/strength/maximum")
    public ResponseEntity<Object> maximumEweStrengthInTimeRange(
            @RequestParam ExtremeWeatherEventCategory extremeWeatherEventCategory,
            @RequestParam String cityId,
            @RequestParam LocalDateTime startDate,
            @RequestParam LocalDateTime endDate
    ) {
        List<Document> serviceResponse = analyticsService.maximumEweStrengthInTimeRange(extremeWeatherEventCategory, startDate, endDate, cityId);
        return ResponseEntity.status(HttpStatus.OK).body(serviceResponse);
    }

    @GetMapping("/ewe/strength/average")
    public ResponseEntity<Object> averageEweStrengthInTimeRange(
            @RequestParam ExtremeWeatherEventCategory extremeWeatherEventCategory,
            @RequestParam String cityId,
            @RequestParam LocalDateTime startDate,
            @RequestParam LocalDateTime endDate
    ) {
        List<Document> serviceResponse = analyticsService.averageEweStrengthInTimeRange(extremeWeatherEventCategory, startDate, endDate, cityId);
        return ResponseEntity.status(HttpStatus.OK).body(serviceResponse);
    }

    @GetMapping("/ewe/duration/longest")
    public ResponseEntity<Object> longestEweDurationInTimeRange(
            @RequestParam ExtremeWeatherEventCategory extremeWeatherEventCategory,
            @RequestParam String cityId,
            @RequestParam LocalDateTime startDate,
            @RequestParam LocalDateTime endDate
    ){
        List<Document> serviceResponse = analyticsService.longestDurationEweInTimeRange(extremeWeatherEventCategory, startDate, endDate, cityId);
        return ResponseEntity.status(HttpStatus.OK).body(serviceResponse);
    }

    @GetMapping("/ewe/duration/average")
    public ResponseEntity<Object> averageEweDurationInTimeRange(
            @RequestParam ExtremeWeatherEventCategory extremeWeatherEventCategory,
            @RequestParam String cityId,
            @RequestParam LocalDateTime startDate,
            @RequestParam LocalDateTime endDate
    ){
        List<Document> serviceResponse = analyticsService.averageDurationEweInTimeRange(extremeWeatherEventCategory, startDate, endDate, cityId);
        return ResponseEntity.status(HttpStatus.OK).body(serviceResponse);
    }

    // </editor-fold>

}
