package it.unipi.lsmsd.service;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Accumulators.*;
import static com.mongodb.client.model.Aggregates.*;
import static com.mongodb.client.model.Projections.*;
import static com.mongodb.client.model.Sorts.*;
import static it.unipi.lsmsd.utility.MeasurementUtility.getFieldName;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Field;
import it.unipi.lsmsd.model.ExtremeWeatherEventCategory;
import it.unipi.lsmsd.model.MeasurementField;
import org.bson.Document;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
public class AnalyticsService{

    private final MongoCollection<Document> measurementCollection;
    private final MongoCollection<Document> cityCollection;

    public AnalyticsService() {
        MongoClient mongoClient = MongoClients.create(
                MongoClientSettings.builder()
                        .applyConnectionString(new ConnectionString("mongodb://localhost:27018"))
                        .build()
        );
        MongoDatabase database = mongoClient.getDatabase("WeatherApp");
        this.measurementCollection = database.getCollection("hourly_measurements");
        this.cityCollection = database.getCollection("cities");
    }

    // <editor-fold desc="Measurements analytics with single city as target [ measurement/city/ ]">

    /**
     * Retrieves the number of measurements recorded for each city within a specified time interval.
     * The results are sorted in descending order by the count of measurements, and in ascending order
     * by city identifier in case of ties.
     *
     * @param startDate the start of the time interval (inclusive)
     * @param endDate   the end of the time interval (exclusive)
     * @return a list of documents, each containing a city identifier and the corresponding number of measurements
     */
    public List<Document> getMeasurementCountByCityInRange(
            LocalDateTime startDate,
            LocalDateTime endDate
    ) {
        String projectedName = "Number of measurements";
        return StreamSupport.stream(measurementCollection.aggregate(
                Arrays.asList(
                        match(and(
                                gte("time", startDate),
                                lt("time", endDate)
                        )),
                        group("$cityId", sum(projectedName, 1)),
                        sort(orderBy(
                                descending(projectedName),
                                ascending("_id")
                        ))
                )).spliterator(), false)
                .collect(Collectors.toList());
    }

    public List<Document> getMeasurementsList(
            String cityId,
            MeasurementField measurementField,
            LocalDateTime startDate,
            LocalDateTime endDate
    ){
        String fieldName = getFieldName(measurementField);

        return StreamSupport.stream(measurementCollection.aggregate(
                        Arrays.asList(
                                match(and(
                                        eq("cityId", cityId),
                                        gte("time", startDate),
                                        lte("time", endDate)
                                )),
                                project(fields(
                                        excludeId(),
                                        include("time", fieldName)
                                ))
                        )).spliterator(), false)
                .collect(Collectors.toList());
    }

    /**
     * Computes the average measurement field value measured in a specific city during a given time interval.
     *
     * @param cityId    the ID of the target city
     * @param startDate the start of the time interval (inclusive)
     * @param endDate   the end of the time interval (inclusive)
     * @return a list containing a single document with the average measurement field value,
     *      or an empty list if no data is found
     */
    public List<Document> averageMeasurementInCityDuringPeriod(
            String cityId,
            MeasurementField measurementField,
            LocalDateTime startDate,
            LocalDateTime endDate
    ) {
        String fieldName = getFieldName(measurementField);
        String expression = "$" + fieldName;
        String projectedName = "Average " + fieldName;

        return StreamSupport.stream(measurementCollection.aggregate(
                Arrays.asList(
                        match(and(
                                eq("cityId", cityId),
                                gte("time", startDate),
                                lte("time", endDate)
                        )),
                        group(
                                "$cityId",
                                avg(projectedName, expression)
                        ),
                        project(fields(
                                computed("cityId", "$_id"),
                                computed(projectedName, "$" + projectedName)
                        ))
                )).spliterator(), false)
                .collect(Collectors.toList());

    }

    /**
     * Computes the average of a specified measurement field, grouped by month, for a given city
     * within the specified time interval.
     *
     * <p>The function expands the provided temporal range to include the full months in which
     * the start and end dates fall. It then performs an aggregation over the
     * {@code hourly_measurements} collection to calculate the monthly average of the
     * selected field.</p>
     *
     * @param cityId the unique identifier of the target city.
     * @param measurementField the type of measurement to average (e.g., temperature, wind speed).
     * @param startDate the start of the date range to analyze (inclusive).
     * @param endDate the end of the date range to analyze (inclusive).
     * @return a list of {@link org.bson.Document} objects representing the monthly averages for the city.
     */
    public List<Document> averageMeasurementGroupByMonthInCityDuringPeriod(
            String cityId,
            MeasurementField measurementField,
            LocalDateTime startDate,
            LocalDateTime endDate
    ) {
        // Round start to the start of its month
        // Round end to teh end of its month
        startDate = startDate.withDayOfMonth(1).with(LocalTime.MIN);
        endDate = endDate.withDayOfMonth(endDate.toLocalDate().lengthOfMonth()).with(LocalTime.MAX);

        String fieldName = getFieldName(measurementField);
        String expression = "$" + fieldName;
        String projectedName = "Average " + fieldName;

        return StreamSupport.stream(measurementCollection.aggregate(
                Arrays.asList(
                        match(and(
                                eq("cityId", cityId),
                                gte("time", startDate),
                                lte("time", endDate)
                        )),
                        addFields(new Field<>("month", new Document("$month", "$time"))),
                        group(new Document("month", "$month"),avg(projectedName, expression)),
                        sort(orderBy(ascending("_id.month"))),
                        project(fields(
                                computed("MonthId", "$_id.month"),
                                excludeId(),
                                include(projectedName)
                        ))
                )).spliterator(), false)
                .collect(Collectors.toList());
    }

    /**
     * Computes the max N measurements field value measured in a specific city during a given time interval.
     *
     * @param cityId                the ID of the target city
     * @param startDate             the start of the time interval (inclusive)
     * @param endDate               the end of the time interval (inclusive)
     * @param measurementField      the field to search
     * @param numMeasurementsToFind number of measurements to find
     * @return a list containing a single document with the max N measurements field values,
     *      or an empty list if no data is found
     */
    public List<Document> highestMeasurementsInCityDuringPeriod(
            String cityId,
            MeasurementField measurementField,
            LocalDateTime startDate,
            LocalDateTime endDate,
            int numMeasurementsToFind
    ) {
        String fieldName = getFieldName(measurementField);

        return StreamSupport.stream(measurementCollection.aggregate(
                Arrays.asList(
                        match(and(
                                eq("cityId", cityId),
                                gte("time", startDate),
                                lte("time", endDate)
                        )),
                        sort(orderBy(descending(fieldName))),

                        // Limit to the top X cities with the highest average rainfall
                        limit(numMeasurementsToFind),

                        // Add day and timeOfDay from "time"
                        addFields(
                                new Field<>("day", new Document("$dateToString", new Document()
                                        .append("format", "%Y-%m-%d")
                                        .append("date", "$time"))),
                                new Field<>("timeOfDay", new Document("$dateToString", new Document()
                                        .append("format", "%H:%M:%S")
                                        .append("date", "$time")))
                        ),

                        // Project only the required fields
                        project(fields(
                                include("cityId", "day", "timeOfDay", fieldName)
                        ))
                )).spliterator(), false)
                .collect(Collectors.toList());
    }

    /**
     * Computes the min N measurements field value measured in a specific city during a given time interval.
     *
     * @param cityId                the ID of the target city
     * @param startDate             the start of the time interval (inclusive)
     * @param endDate               the end of the time interval (inclusive)
     * @param measurementField      the field to search
     * @param numMeasurementsToFind number of measurements to find
     * @return a list containing the documents with the min N measurements field values,
     *         or an empty list if no data is found
     */
    public List<Document> lowestMeasurementsInCityDuringPeriod(
            String cityId,
            MeasurementField measurementField,
            LocalDateTime startDate,
            LocalDateTime endDate,
            int numMeasurementsToFind
    ) {
        String fieldName = getFieldName(measurementField);

        return StreamSupport.stream(measurementCollection.aggregate(
                Arrays.asList(
                        match(and(
                                eq("cityId", cityId),
                                gte("time", startDate),
                                lte("time", endDate)
                        )),
                        sort(orderBy(ascending(fieldName))),
                        limit(numMeasurementsToFind),
                        addFields(
                                new Field<>("day", new Document("$dateToString", new Document()
                                        .append("format", "%Y-%m-%d")
                                        .append("date", "$time"))),
                                new Field<>("timeOfDay", new Document("$dateToString", new Document()
                                        .append("format", "%H:%M:%S")
                                        .append("date", "$time")))
                        ),
                        project(fields(
                                include("cityId", "day", "timeOfDay", fieldName)
                        ))
                )).spliterator(), false)
                .collect(Collectors.toList());
    }

    // </editor-fold>

    // <editor-fold desc="Measurements analytics with region as target [ measurement/region/ ]">

    /**
     * Computes the top cities with the highest average value of a specific measurement field
     * during a specified time period. The cities are enriched with metadata from the "cities" collection.
     *
     * @param measurementField   the measurement field to average (e.g., temperature, rainfall)
     * @param startDate          the start of the time interval (inclusive)
     * @param endDate            the end of the time interval (inclusive)
     * @param region             the target region
     * @return a list of documents representing the top cities with their average measurement and metadata
     */
    public List<Document> highestAverageMeasurementInRegion(
            MeasurementField measurementField,
            LocalDateTime startDate,
            LocalDateTime endDate,
            String region
    ) {
        String fieldName = getFieldName(measurementField);
        String expression = "$" + fieldName;
        String projectedName = "Average " + fieldName;

        String regionPrefix = region.substring(0, 3).toLowerCase();

        return StreamSupport.stream(measurementCollection.aggregate(
                Arrays.asList(
                        match(and(
                                regex("cityId", "^" + regionPrefix + "-"),
                                gte("time", startDate),
                                lte("time", endDate)
                        )),
                        group("$cityId", avg(projectedName, expression)),
                        sort(descending(projectedName))
                        )).spliterator(), false)
                .collect(Collectors.toList());
    }

    /**
     * Computes the top cities with the lowest average value of a specific measurement field
     * during a specified time period. The cities are enriched with metadata from the "cities" collection.
     *
     * @param measurementField   the measurement field to average (e.g., temperature, rainfall)
     * @param startDate          the start of the time interval (inclusive)
     * @param endDate            the end of the time interval (inclusive)
     * @param region             the target region
     * @return a list of documents representing the top cities with their average measurement and metadata
     */
    public List<Document> lowestAverageMeasurementInRegion(
            MeasurementField measurementField,
            LocalDateTime startDate,
            LocalDateTime endDate,
            String region
    ) {
        String fieldName = getFieldName(measurementField);
        String expression = "$" + fieldName;
        String projectedName = "Average " + fieldName;

        String regionPrefix = region.substring(0, 3).toLowerCase();

        return StreamSupport.stream(measurementCollection.aggregate(
                Arrays.asList(
                        match(and(
                                regex("cityId", "^" + regionPrefix + "-"),
                                gte("time", startDate),
                                lte("time", endDate)
                        )),
                        group("$cityId", avg(projectedName, expression)),
                        sort(ascending(projectedName))
                )).spliterator(), false)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves the top cities with the highest recorded value of a specific measurement field
     * (e.g., temperature, rainfall) within a specified time interval. For each city, the time
     * of the maximum measurement is preserved and formatted into day and time-of-day components.
     *
     * @param measurementField   the measurement field to evaluate (e.g., temperature, rainfall)
     * @param startDate          the start of the time interval (inclusive)
     * @param endDate            the end of the time interval (inclusive)
     * @param region             the target region
     * @return a list of documents containing the city ID, measurement value, and timestamp information
     */
    public List<Document> highestMeasurementInRegion(
            MeasurementField measurementField,
            LocalDateTime startDate,
            LocalDateTime endDate,
            String region
    ) {
        String fieldName = getFieldName(measurementField);
        String expression = "$" + fieldName;
        String projectedName = "Maximum " + fieldName;

        String regionPrefix = region.substring(0, 3).toLowerCase();

        return StreamSupport.stream(measurementCollection.aggregate(
                Arrays.asList(
                        match(and(
                                regex("cityId", "^" + regionPrefix + "-"),
                                gte("time", startDate),
                                lte("time", endDate)
                        )),
                        // First sort, in order to be able to take the first element after
                        sort(orderBy(ascending("cityId"), descending(fieldName))),
                        group("$cityId",
                                first("time", "$time"),
                                first(projectedName, expression)
                        ),
                        addFields(
                                new Field<>("day", new Document("$dateToString", new Document()
                                        .append("format", "%Y-%m-%d")
                                        .append("date", "$time"))),
                                new Field<>("timeOfDay", new Document("$dateToString", new Document()
                                        .append("format", "%H:%M:%S")
                                        .append("date", "$time")))
                        ),
                        project(fields(
                                include( projectedName, "day", "timeOfDay")
                        ))
                )).spliterator(), false)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves the top cities with the lowest recorded value of a specific measurement field
     * (e.g., temperature, rainfall) within a specified time interval. For each city, the time
     * of the maximum measurement is preserved and formatted into day and time-of-day components.
     *
     * @param measurementField   the measurement field to evaluate (e.g., temperature, rainfall)
     * @param startDate          the start of the time interval (inclusive)
     * @param endDate            the end of the time interval (inclusive)
     * @param region             the target region
     * @return a list of documents containing the city ID, measurement value, and timestamp information
     */
    public List<Document> lowestMeasurementInRegion(
            MeasurementField measurementField,
            LocalDateTime startDate,
            LocalDateTime endDate,
            String region
    ) {
        String fieldName = getFieldName(measurementField);
        String expression = "$" + fieldName;
        String projectedName = "Minimum " + fieldName;

        String regionPrefix = region.substring(0, 3).toLowerCase();

        return StreamSupport.stream(measurementCollection.aggregate(
                        Arrays.asList(
                                match(and(
                                        regex("cityId", "^" + regionPrefix + "-"),
                                        gte("time", startDate),
                                        lte("time", endDate)
                                )),
                                // First sort, in order to be able to take the first element after
                                sort(orderBy(ascending("cityId"), ascending(fieldName))),
                                group("$cityId",
                                        first("time", "$time"),
                                        first(projectedName, expression)
                                ),
                                addFields(
                                        new Field<>("day", new Document("$dateToString", new Document()
                                                .append("format", "%Y-%m-%d")
                                                .append("date", "$time"))),
                                        new Field<>("timeOfDay", new Document("$dateToString", new Document()
                                                .append("format", "%H:%M:%S")
                                                .append("date", "$time")))
                                ),
                                project(fields(
                                        include( projectedName, "day", "timeOfDay")
                                ))
                        )).spliterator(), false)
                .collect(Collectors.toList());
    }

    // </editor-fold>

    // <editor-fold desc="Measurements analytics of recent period [ measurement/recent/ ]">

    /**
     * Computes the daily average of the specified measurement field for a given city,
     * considering only the past specified number of full days (excluding today).
     * Each result document contains the day (formatted as YYYY-MM-DD) and the corresponding average.
     *
     * @param measurementField the field (e.g., temperature, rainfall) whose daily average is to be computed
     * @param cityId the identifier of the city
     * @param pastDays the number of full past days to include (e.g., 1 = yesterday only)
     * @return a list of {@link Document} objects, each containing the day and average value
     */
    public List<Document> recentAverageMeasurementPerDayOfCity(
            MeasurementField measurementField,
            String cityId,
            int pastDays
    ){
        String fieldName = getFieldName(measurementField);
        String expression = "$" + fieldName;
        String projectedName = "Average " + fieldName;

        // Compute the start of the day (00:00) for the (today - pastDays) date
        LocalDateTime dateLimit = LocalDate.now()
                .minusDays(pastDays)
                .atStartOfDay();

        return StreamSupport.stream(measurementCollection.aggregate(
                        Arrays.asList(
                                match(and(
                                        eq("cityId", cityId),
                                        gte("time", dateLimit)
                                )),
                                addFields(
                                        new Field<>("day", new Document(
                                                "$dateToString", new Document()
                                                .append("format", "%Y-%m-%d")
                                                .append("date", "$time")
                                        ))),
                                group("$day", avg(projectedName, expression)),
                                sort(descending("_id")) // after group day is the id
                        )).spliterator(), false)
                .collect(Collectors.toList());
    }

    /**
     * Computes the total (sum) of the specified measurement field for each day
     * in a given city, over the past specified number of full days (excluding today).
     * The results are grouped by day (YYYY-MM-DD) and sorted in descending order by date.
     * <p>
     * Note: This method is meaningful only for cumulative metrics such as rainfall or snowfall,
     * not for instantaneous values like temperature or wind speed.
     *
     * @param measurementField the field (e.g., rainfall, snowfall) whose total is to be computed
     * @param cityId the identifier of the city
     * @param pastDays the number of full past days to include (e.g., 1 = yesterday only)
     * @return a list of {@link Document} objects, each containing a day and the total value
     */
    public List<Document> recentTotalMeasurementPerDayOfCity(
            MeasurementField measurementField,
            String cityId,
            int pastDays
    ){
        String fieldName = getFieldName(measurementField);
        String expression = "$" + fieldName;
        String projectedName = "Total " + fieldName;

        // Compute the start of the day (00:00) for the (today - pastDays) date
        LocalDateTime dateLimit = LocalDate.now()
                .minusDays(pastDays)
                .atStartOfDay();

        return StreamSupport.stream(measurementCollection.aggregate(
                        Arrays.asList(
                                match(and(
                                        eq("cityId", cityId),
                                        gte("time", dateLimit)
                                )),
                                addFields(
                                        new Field<>("day", new Document(
                                                "$dateToString", new Document()
                                                .append("format", "%Y-%m-%d")
                                                .append("date", "$time")
                                        ))),
                                group("$day", sum(projectedName, expression)),
                                sort(descending("_id")) // after group day is the id
                        )).spliterator(), false)
                .collect(Collectors.toList());
    }

    /**
     * Computes the average value of the specified measurement field for each city,
     * considering only data from the specified number of past days.
     * The results are sorted in descending order based on the computed average.
     *
     * @param measurementField the field (e.g., temperature, rainfall) whose average is to be computed
     * @param region target region
     * @param pastDays the number of full past days to include in the aggregation (e.g., 1 = yesterday only)
     * @return a list of {@link Document} objects, each containing a cityId and the average value
     */
    public List<Document> getAverageMeasurementOfLastDaysOfRegion(
            MeasurementField measurementField,
            String region,
            int pastDays
    ){
        String fieldName = getFieldName(measurementField);
        String expression = "$" + fieldName;
        String projectedName = "Average " + fieldName;

        String regionPrefix = region.substring(0, 3).toLowerCase();

        // Compute the start of the day (00:00) for the (today - pastDays) date
        LocalDateTime dateLimit = LocalDate.now()
                .minusDays(pastDays)
                .atStartOfDay();

        return StreamSupport.stream(measurementCollection.aggregate(
                        Arrays.asList(
                                match(and(
                                        regex("cityId", "^" + regionPrefix + "-"),
                                        gte("time", dateLimit)
                                )),
                                group("$cityId", avg(projectedName, expression)),
                                sort(descending(projectedName)),
                                project(fields(include(projectedName)))
                        )).spliterator(), false)
                .collect(Collectors.toList());
    }

    /**
     * Computes the total (sum) of the specified measurement field for each city,
     * considering only data from the specified number of past days (excluding today).
     * The results are sorted in descending order based on the computed total.
     *
     * @param measurementField the field (e.g., temperature, rainfall) whose total is to be computed
     * @param region target region
     * @param pastDays the number of full past days to include in the aggregation (e.g., 1 = yesterday only)
     * @return a list of {@link Document} objects, each containing a cityId and the total value
     */
    public List<Document> getTotalMeasurementLastDaysOfRegion(
            MeasurementField measurementField,
            String region,
            int pastDays
    ){
        String fieldName = getFieldName(measurementField);
        String expression = "$" + fieldName;
        String projectedName = "Total " + fieldName;

        String regionPrefix = region.substring(0, 3).toLowerCase();

        // Compute the start of the day (00:00) for the (today - pastDays) date
        LocalDateTime dateLimit = LocalDate.now()
                .minusDays(pastDays)
                .atStartOfDay();

        return StreamSupport.stream(measurementCollection.aggregate(
                        Arrays.asList(
                                match(and(
                                        regex("cityId", "^" + regionPrefix + "-"),
                                        gte("time", dateLimit)
                                )),
                                group("$cityId", sum(projectedName, expression)),
                                sort(descending(projectedName)),
                                project(fields(include(projectedName)))
                        )).spliterator(), false)
                .collect(Collectors.toList());
    }

    // </editor-fold>



    // <editor-fold desc="Extreme Weather Event analytics across multiple cities [ ewe/ ]">

    /**
     * Retrieves the top cities most affected by extreme weather events (EWEs) of a specified category
     * within a given time range. The cities are ranked by the number of EWE occurrences and limited
     * to a specified maximum number of results.
     *
     * @param EweCategory        the category of extreme weather events to consider
     * @param startDate          the start of the time interval (inclusive)
     * @param endDate            the end of the time interval (inclusive)
     * @return a list of documents, each containing a city identifier and the corresponding count of EWEs
     */
    public List<Document> citiesMostAffectedByEweInTimeRange(
            ExtremeWeatherEventCategory EweCategory,
            LocalDateTime startDate,
            LocalDateTime endDate,
            String cityId
    ) {
        String projectedName = "ExtremeWeatherEvent count";
        return StreamSupport.stream(cityCollection.aggregate(
                        Arrays.asList(
                                match(eq("_id", cityId)),
                                unwind("$eweList"),
                                match(and(
                                        eq("eweList.category", EweCategory.name().toUpperCase()),
                                        gte("eweList.dateStart", startDate),
                                        lte("eweList.dateEnd", endDate)
                                )),
                                group(
                                        "$_id",
                                        sum(projectedName, 1)
                                ),
                                project(fields(
                                        computed("cityId", "$_id"),
                                        include(projectedName)
                                )),
                                sort(orderBy(
                                        descending(projectedName)
                                ))
                        )).spliterator(), false)
                .collect(Collectors.toList());
    }


    /**
     * Retrieves the number of extreme weather events (EWEs) for each city that match a specified category,
     * have a strength greater than or equal to the given threshold, and fall within the specified time range.
     *
     * <p>The result is a list of documents containing:
     * <ul>
     *     <li><b>cityId</b>: the unique identifier of the city.</li>
     *     <li><b>ExtremeWeatherEvent count</b>: the number of matching EWEs for that city.</li>
     * </ul>
     * </p>
     *
     * @param minimumStrength the minimum strength threshold that a EWE must satisfy to be included.
     * @param EweCategory the category of the extreme weather events (e.g., RAINFALL, TEMPERATURE, etc.).
     * @param startDate the start of the temporal window (inclusive) within which to search for events.
     * @param endDate the end of the temporal window (inclusive) within which to search for events.
     * @return a list of {@link org.bson.Document} objects, each representing a city and its corresponding number of EWEs.
     */
    public List<Document> numberOfEweOfStrengthInTimeRange(
            int minimumStrength,
            ExtremeWeatherEventCategory EweCategory,
            LocalDateTime startDate,
            LocalDateTime endDate,
            String cityId
    ) {
        String projectedName = "ExtremeWeatherEvent count";

        return StreamSupport.stream(cityCollection.aggregate(Arrays.asList(
                        match(eq("_id", cityId)),         // seleziona città della regione
                        unwind("$eweList"),                                    // eventi singoli
                        match(and(
                                eq("eweList.category", EweCategory.name().toUpperCase()),
                                gte("eweList.dateStart", startDate),
                                lte("eweList.dateEnd", endDate),
                                gte("eweList.strength", minimumStrength)
                        )),
                        group(
                                "$_id",                                        // raggruppa per id città
                                sum(projectedName, 1)
                        ),
                        project(fields(
                                computed("cityId", "$_id"),
                                include(projectedName)
                        )),
                        sort(orderBy(descending(projectedName)))
                )).spliterator(), false)
                .collect(Collectors.toList());
    }


    /**
     * Retrieves the maximum strength of a specific extreme weather event (EWE) category
     * for each city within the specified date range.
     *
     * <p>This method filters the EWE documents by category and time interval,
     * then groups them by city and extracts the maximum recorded strength.</p>
     *
     * <p>The result includes:
     * <ul>
     *     <li><b>cityId</b>: the identifier of the city</li>
     *     <li><b>Maximum strength</b>: the highest strength value of the event category in the time window</li>
     * </ul>
     * </p>
     *
     * @param EweCategory the category of the extreme weather event to consider.
     * @param startDate the start of the time range (inclusive).
     * @param endDate the end of the time range (inclusive).
     * @return a list of documents, each containing the city ID and the maximum strength observed.
     */
    public List<Document> maximumEweStrengthInTimeRange(
            ExtremeWeatherEventCategory EweCategory,
            LocalDateTime startDate,
            LocalDateTime endDate,
            String cityId
    ) {
        String projectedName = "Maximum strength";

        return StreamSupport.stream(cityCollection.aggregate(Arrays.asList(
                        match(eq("_id", cityId)),
                        unwind("$eweList"),
                        match(and(
                                eq("eweList.category", EweCategory.name().toUpperCase()),
                                gte("eweList.dateStart", startDate),
                                lte("eweList.dateEnd", endDate)
                        )),
                        sort(orderBy(descending("eweList.strength"))),
                        group(
                                "$_id",
                                first(projectedName, "$eweList.strength"),
                                first("dateStart", "$eweList.dateStart"),
                                first("dateEnd", "$eweList.dateEnd")
                        ),
                        project(fields(
                                computed("cityId", "$_id"),
                                include(projectedName, "dateStart", "dateEnd")
                        )),
                        sort(orderBy(descending(projectedName)))
                )).spliterator(), false)
                .collect(Collectors.toList());
    }


    /**
     * Retrieves the average strength of a specific extreme weather event (EWE) category
     * for each city within the specified date range.
     *
     * <p>This method filters the EWE documents by category and time interval,
     * then groups them by city and extracts the average recorded strength.</p>
     *
     * <p>The result includes:
     * <ul>
     *     <li><b>cityId</b>: the identifier of the city</li>
     *     <li><b>Average strength</b>: the average strength value of the event category in the time window</li>
     * </ul>
     * </p>
     *
     * @param EweCategory the category of the extreme weather event to consider.
     * @param startDate the start of the time range (inclusive).
     * @param endDate the end of the time range (inclusive).
     * @return a list of documents, each containing the city ID and the maximum strength observed.
     */
    public List<Document> averageEweStrengthInTimeRange(
            ExtremeWeatherEventCategory EweCategory,
            LocalDateTime startDate,
            LocalDateTime endDate,
            String cityId
    ) {
        String projectedName = "Average strength";

        return StreamSupport.stream(cityCollection.aggregate(Arrays.asList(
                        match(eq("_id", cityId)),
                        unwind("$eweList"),
                        match(and(
                                eq("eweList.category", EweCategory.name().toUpperCase()),
                                gte("eweList.dateStart", startDate),
                                lte("eweList.dateEnd", endDate)
                        )),
                        group(
                                "$_id",
                                avg(projectedName, "$eweList.strength")
                        ),
                        project(fields(
                                computed("cityId", "$_id"),
                                include(projectedName)
                        )),
                        sort(orderBy(descending(projectedName)))
                )).spliterator(), false)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves, for each city, the longest extreme weather event (EWE) of the specified category
     * occurring within the given date range. The result includes the event's duration in hours,
     * along with the start date, end date, and strength of the event.
     * Events without a valid end date are excluded.
     *
     * @param EweCategory the category of the extreme weather event (e.g., HEATWAVE, FLOOD)
     * @param startDate the inclusive lower bound of the event start date
     * @param endDate the inclusive upper bound of the event end date
     * @return a list of {@link Document} objects, each containing the cityId, duration, and EWE details
     */
    public List<Document> longestDurationEweInTimeRange(
            ExtremeWeatherEventCategory EweCategory,
            LocalDateTime startDate,
            LocalDateTime endDate,
            String cityId
    ) {
        String projectedName = "ExtremeWeatherEvent duration (hours)";
        return StreamSupport.stream(cityCollection.aggregate(
                        Arrays.asList(
                                match(eq("_id", cityId)),
                                unwind("$eweList"),
                                match(and(
                                        eq("eweList.category", EweCategory.name().toUpperCase()),
                                        gte("eweList.dateStart", startDate),
                                        lte("eweList.dateEnd", endDate),
                                        exists("eweList.dateEnd", true),
                                        ne("eweList.dateEnd", null)
                                )),
                                addFields(new Field<>(
                                        "durationHours",
                                        new Document(
                                                "$divide", Arrays.asList(
                                                new Document(
                                                        "$subtract",
                                                        Arrays.asList("$eweList.dateEnd", "$eweList.dateStart")
                                                ),
                                                1000 * 60 * 60
                                        )
                                        )
                                )),
                                sort(descending("durationHours")),
                                group("$_id",
                                        first("dateStart", "$eweList.dateStart"),
                                        first("dateEnd", "$eweList.dateEnd"),
                                        first("strength", "$eweList.strength"),
                                        first(projectedName, "$durationHours")
                                ),
                                project(fields(
                                        computed("cityId", "$_id"),
                                        include("dateStart", "dateEnd", "strength", projectedName)
                                )),
                                sort(orderBy(descending(projectedName)))
                        )).spliterator(), false)
                .collect(Collectors.toList());
    }


    /**
     * Computes the average duration (in hours) of extreme weather events (EWEs)
     * of the specified category for each city, within the given time range.
     * Only events with a non-null end date are considered.
     *
     * @param EweCategory the category of the extreme weather event (e.g., HEATWAVE, FLOOD)
     * @param startDate the inclusive lower bound for the event start date
     * @param endDate the inclusive upper bound for the event end date
     * @return a list of {@link Document} objects, each containing the cityId and average duration
     */
    public List<Document> averageDurationEweInTimeRange(
            ExtremeWeatherEventCategory EweCategory,
            LocalDateTime startDate,
            LocalDateTime endDate,
            String cityId
    ) {
        String projectedName = "ExtremeWeatherEvent average duration (hours)";
        return StreamSupport.stream(cityCollection.aggregate(
                        Arrays.asList(
                                match(eq("_id", cityId)),
                                unwind("$eweList"),
                                match(and(
                                        eq("eweList.category", EweCategory.name().toUpperCase()),
                                        gte("eweList.dateStart", startDate),
                                        lte("eweList.dateEnd", endDate),
                                        exists("eweList.dateEnd", true),
                                        ne("eweList.dateEnd", null)
                                )),
                                addFields(new Field<>(
                                        "durationHours",
                                        new Document(
                                                "$divide", Arrays.asList(
                                                new Document(
                                                        "$subtract",
                                                        Arrays.asList("$eweList.dateEnd", "$eweList.dateStart")
                                                ),
                                                1000 * 60 * 60
                                        )
                                        )
                                )),
                                group("$_id",
                                        avg(projectedName, "$durationHours")
                                ),
                                project(fields(
                                        computed("cityId", "$_id"),
                                        include(projectedName)
                                )),
                                sort(orderBy(descending(projectedName)))
                        )).spliterator(), false)
                .collect(Collectors.toList());
    }


    /**
     * Computes the average number of extreme weather events for each calendar month
     * across the specified time range, for a given city and category.
     * It aggregates counts per month-year and then averages over each month.
     *
     * @param cityId the identifier of the city
     * @param EweCategory the category of extreme weather events
     * @param startDate the inclusive lower bound of the event start date
     * @param endDate the inclusive upper bound of the event start date
     * @return a list of {@link Document} containing a single document with the field
     *         "averageMonthlyCount" representing the average number of events per month
     */
    public List<Document> eweCountByMonth(
            String cityId,
            ExtremeWeatherEventCategory EweCategory,
            LocalDateTime startDate,
            LocalDateTime endDate
    ) {
        String projectedName = "ExtremeWeatherEvent count";
        return StreamSupport.stream(cityCollection.aggregate(
                        Arrays.asList(
                                match(eq("_id", cityId)),
                                unwind("$eweList"),
                                match(and(
                                        eq("eweList.category", EweCategory.name().toUpperCase()),
                                        gte("eweList.dateStart", startDate),
                                        lte("eweList.dateStart", endDate)
                                )),
                                addFields(new Field<>("month", new Document("$month", "$eweList.dateStart"))),
                                group(
                                        "$month",
                                        sum(projectedName, 1)
                                ),
                                sort(ascending("_id")),
                                project(fields(
                                        computed("MonthId", "$_id"),
                                        excludeId(),
                                        include(projectedName)
                                ))
                        )).spliterator(), false)
                .collect(Collectors.toList());
    }

    // </editor-fold>

}
