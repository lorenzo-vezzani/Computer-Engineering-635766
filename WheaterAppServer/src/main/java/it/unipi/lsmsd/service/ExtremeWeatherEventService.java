package it.unipi.lsmsd.service;

import it.unipi.lsmsd.exception.CityNotFoundException;
import it.unipi.lsmsd.exception.ThresholdsNotPresentException;
import it.unipi.lsmsd.model.*;
import it.unipi.lsmsd.repository.CityRepository;
//import it.unipi.lsmsd.repository.ExtremeWeatherEventRepository;
import it.unipi.lsmsd.repository.HourlyMeasurementRepository;
import it.unipi.lsmsd.utility.QuadrupleEWEInformationHolder;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

import static it.unipi.lsmsd.utility.CityUtility.hasCityAllThresholdsFields;
import static it.unipi.lsmsd.utility.EWEUtility.getCurrentEWEs;
import static it.unipi.lsmsd.utility.EWEUtility.getEmptyListOfEWEs;

@Service
public class ExtremeWeatherEventService {

    //@Autowired
    //private ExtremeWeatherEventRepository eweRepository;
    @Autowired
    private HourlyMeasurementRepository hourlyMeasurementRepository;
    @Autowired
    private CityRepository cityRepository;
    @Autowired
    private CityService cityService;

    public List<ExtremeWeatherEvent> getEventsOfCity(String cityId) {
        return cityRepository.findById(cityId)
                .map(City::getEweList)
                .orElse(Collections.emptyList());
    }

    public List<ExtremeWeatherEvent> getOngoingEvents(String cityId) {
        return cityRepository.findCityWithOngoingEvents(cityId)
                .map(city -> city.getEweList().stream()
                        .filter(e -> e.getDateEnd() == null)
                        .collect(Collectors.toList()))
                .orElse(Collections.emptyList());
    }

    public List<ExtremeWeatherEvent> getEventsByCategorySorted(String cityId, ExtremeWeatherEventCategory category) {
        return cityRepository.findCityWithEventsOfCategory(cityId, category.name())
                .map(city -> city.getEweList().stream()
                        .filter(e -> category.equals(e.getCategory()))
                        .sorted(Comparator.comparing(ExtremeWeatherEvent::getDateStart))
                        .collect(Collectors.toList()))
                .orElse(Collections.emptyList());
    }

    public List<ExtremeWeatherEvent> getEventsOngoingAt(String cityId, LocalDateTime time) {
        return cityRepository.findCityWithEventsStartedBefore(cityId, time)
                .map(city -> city.getEweList().stream()
                        .filter(e -> e.getDateEnd() == null && !e.getDateStart().isAfter(time))
                        .collect(Collectors.toList()))
                .orElse(Collections.emptyList());
    }

    public List<ExtremeWeatherEvent> getEventsInRangeWithCategory(String cityId, ExtremeWeatherEventCategory category, LocalDateTime start, LocalDateTime end) {
        return cityRepository.findCityWithEventsInRangeAndCategory(cityId, category.name(), start, end)
                .map(city -> city.getEweList().stream()
                        .filter(e -> category.equals(e.getCategory()) &&
                                (e.getDateStart().isEqual(start) || e.getDateStart().isAfter(start)) &&
                                (e.getDateStart().isEqual(end) || e.getDateStart().isBefore(end)))
                        .sorted(Comparator.comparing(ExtremeWeatherEvent::getDateStart))
                        .collect(Collectors.toList()))
                .orElse(Collections.emptyList());
    }


    public List<ExtremeWeatherEvent> updateExtremeWeatherEventAutomatic(
            String cityId
    ) throws CityNotFoundException, ThresholdsNotPresentException {

        // Get city's last Ewe update
        LocalDateTime lastEweUpdate = cityService.getLastEweUpdateById(cityId);

        List<ExtremeWeatherEvent> createdEWEs;

        // If the city has never been updated it calls for the entire time range available
        if(lastEweUpdate == null) {
            createdEWEs = updateExtremeWeatherEventAll(cityId);
        }

        // Calls service updateExtremeWeatherEvent over time interval (lastEweUpdate; Now)
        else {
            createdEWEs = updateExtremeWeatherEvent(cityId, lastEweUpdate, LocalDateTime.now());
        }

        // Update the lastEweUpdate only after successful processing
        cityService.setLastEweUpdateById(cityId, LocalDateTime.now());

        return createdEWEs;
    }
    /**
     * Updates all extreme weather events for the specified city starting from the timestamp
     * of the latest available measurement up to the current time. This method internally
     * delegates to {@code updateExtremeWeatherEvent} using the derived time interval.
     *
     * @param cityId the unique identifier of the city for which to update the extreme weather events
     * @return a list of {@link ExtremeWeatherEvent} objects that have been created during the update process
     * @throws CityNotFoundException If the specified city is not found.
     * @throws ThresholdsNotPresentException If the specified city doesn't have the threshold specified.
     */
    public List<ExtremeWeatherEvent> updateExtremeWeatherEventAll(
            String cityId
    ) throws CityNotFoundException, ThresholdsNotPresentException {

        // Retrieve the timestamp of the latest measurement for the given city
        Optional<HourlyMeasurement> firstMeasurement = hourlyMeasurementRepository.findFirstByCityIdOrderByTimeAsc(cityId);

        if(firstMeasurement.isEmpty()) {
            return Collections.emptyList();
        }

        LocalDateTime firstMeasurementTime = firstMeasurement.get()
                .getTime()
                .toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();

        // Delegate to the main update function using the range [latestMeasurementTime, now]
        return updateExtremeWeatherEvent(cityId, firstMeasurementTime, LocalDateTime.now());
    }

    /**
     * Scans the measurements stored in the database to identify values that exceed predefined thresholds.
     * If such values are found, a new Extreme Weather Event is created for the corresponding category.
     *
     * @param cityId The ID of the city for which measurements should be analyzed.
     * @param startTimeInterval The start time of the interval from which to retrieve measurements.
     * @param endTimeInterval The end time of the interval up to which measurements should be retrieved.
     * @return A list of newly inserted Extreme Weather Events.
     * @throws CityNotFoundException If the specified city is not found.
     * @throws ThresholdsNotPresentException If the specified city doesn't have the threshold specified.
     */
    public List<ExtremeWeatherEvent> updateExtremeWeatherEvent(
            String cityId,
            LocalDateTime startTimeInterval,
            LocalDateTime endTimeInterval
    ) throws CityNotFoundException, ThresholdsNotPresentException {

        // Gets all measurements for a given city
        Date startTime = Date.from(startTimeInterval.toInstant(ZoneOffset.UTC));
        Date endTime = Date.from(endTimeInterval.toInstant(ZoneOffset.UTC));
        List<HourlyMeasurement> hourlyMeasurements = hourlyMeasurementRepository.findByCityIdAndTimeBetweenOrderByTimeTimeAsc(cityId, startTime, endTime);

        // Gets the target city, in order to get the thresholds
        Optional<City> city = cityRepository.findById(cityId);
        if (city.isEmpty())
            throw new CityNotFoundException("Specified city " + cityId + " was not found");

        // Check that the city has all the necessary thresholds
        if(!hasCityAllThresholdsFields(city.get()))
            throw new ThresholdsNotPresentException("City " + cityId + " doesn't have all threshold fields correctly specified");

        // Retrieve the city's extreme weather event thresholds
        EWEThreshold eweThresholds = city.get().getEweThresholds();

        // Array of EWE found at every iteration of the loop
        List<Pair<ExtremeWeatherEventCategory, Integer>> foundEWEs;

        // Working array of found and ongoing EWEs
        List<QuadrupleEWEInformationHolder> ongoingExtremeWeatherEvents = new ArrayList<>(getEmptyListOfEWEs());

        // Ongoing EWE already present in the db
        List<QuadrupleEWEInformationHolder> currentLocalEWEs = getListOfCurrentLocalEWEs(city.get(), startTimeInterval);

        // Putting the currentLocalEWEs into the ongoingExtremeWeatherEvents array
        for (QuadrupleEWEInformationHolder dbEWE : currentLocalEWEs) {
            int index = dbEWE.getCategory().ordinal();
            ongoingExtremeWeatherEvents.set(index, dbEWE);
        }

        // List of completed EWEs, to be returned
        List<ExtremeWeatherEvent> compltedEWEs = new ArrayList<>();

        // Checks all measurements against all thresholds
        for (HourlyMeasurement measurement : hourlyMeasurements) {

            // Gets the list of current EWE
            foundEWEs = getCurrentEWEs(measurement, eweThresholds);

            // Loop over all EWE categories using index,
            // thanks to the fact that the two arrays are always ordinated by category in the same way
            for (int i = 0; i < ExtremeWeatherEventCategory.values().length; i++) {

                // Find the ongoing ewe strength and found ewe strength
                int foundStrength = foundEWEs.get(i).getSecond();
                int ongoingStrength = ongoingExtremeWeatherEvents.get(i).getStrength();

                // Note: strength == 0 means no ewe is found, the measurement value is under the threshold limit

                // Set new startDate if a new EWE is found
                if (foundStrength > 0 &&  ongoingStrength == 0){
                    ongoingExtremeWeatherEvents.get(i).setStrength(foundStrength);
                    ongoingExtremeWeatherEvents.get(i).setDateStart(measurement.getTime());
                }

                // Update ongoing event strength if a greater strength is detected
                if (foundStrength > ongoingStrength && ongoingStrength > 0) {
                    // Updated the strength with the max value
                    ongoingExtremeWeatherEvents.get(i).setStrength(foundStrength);
                }

                // If the found strength is 0 but the event was ongoing, it means the event has ended
                if (foundStrength == 0 && ongoingStrength > 0) {

                    // The EWE has terminated, so it can be saved into the repository
                    ongoingExtremeWeatherEvents.get(i).setDateEnd(measurement.getTime());

                    // Create new EWE,
                    ExtremeWeatherEvent newEWE = createNewEWE(city.get(), ongoingExtremeWeatherEvents.get(i), true);

                    // Add it to repos
                    //ExtremeWeatherEvent insertedEwe = eweRepository.save(newEWE);
                    newEWE.setId(new ObjectId().toHexString());
                    city.get().getEweList().add(newEWE);

                    // Add its id to the list of completed EWEs
                    compltedEWEs.add(newEWE);

                    // Reset the strength to zero and both dates to null
                    ongoingExtremeWeatherEvents.get(i).resetData();
                }
            }
        }

        // Check for still ongoing EWE, save them to DB in the case
        for(QuadrupleEWEInformationHolder ongoingEwe : ongoingExtremeWeatherEvents){

            // The ongoing ewe have a non-null Start Date and a null End Date
            if(ongoingEwe.getDateStart() != null && ongoingEwe.getDateEnd() == null){

                // Create new EWE, without setting the end date field
                ExtremeWeatherEvent newEWE = createNewEWE(city.get(), ongoingEwe, false);

                // Add it to repos
                //ExtremeWeatherEvent insertedEwe = eweRepository.save(newEWE);
                newEWE.setId(new ObjectId().toHexString());
                city.get().getEweList().add(newEWE);
            }
        }

        // Update the city, with all new EWEs inserted in the list
        cityRepository.save(city.get());

        // Returns a list of found and inserted EWEs
        return compltedEWEs;
    }




    /**
     * Retrieves all extreme weather events for a given city across all categories,
     * regardless of time interval, and removes duplicates by merging overlapping events.
     *
     * @param cityId the ID of the city for which to process all extreme weather events
     * @return a map containing the number of removed and inserted extreme weather events
     */
    public Map<String, Integer> cleanExtremeWeatherEventDuplicatesAll(
            String cityId
    ) {

        Map<ExtremeWeatherEventCategory, List<ExtremeWeatherEvent>> eweListsByCategory = new HashMap<>();

        for (ExtremeWeatherEventCategory category : ExtremeWeatherEventCategory.values()) {
            List<ExtremeWeatherEvent> eweList = getEventsByCategorySorted(cityId, category);//eweRepository.findByCityIdAndCategoryOrderByDateStart(cityId, category);
            eweListsByCategory.put(category, eweList);
        }

        return cleanExtremeWeatherEventDuplicates(cityId, eweListsByCategory);
    }


    /**
     * Retrieves all extreme weather events for a given city and time interval,
     * organizes them by category, and removes duplicates by merging overlapping events.
     *
     * @param cityId the ID of the city for which to process extreme weather events
     * @param startTimeInterval the start of the time interval to consider
     * @param endTimeInterval the end of the time interval to consider
     * @return a map containing the number of removed and inserted extreme weather events
     */
    public Map<String, Integer> cleanExtremeWeatherEventDuplicatesRange(
            String cityId,
            LocalDateTime startTimeInterval,
            LocalDateTime endTimeInterval
    ) {

        Map<ExtremeWeatherEventCategory, List<ExtremeWeatherEvent>> eweListsByCategory = new HashMap<>();

        for (ExtremeWeatherEventCategory category : ExtremeWeatherEventCategory.values()) {
            List<ExtremeWeatherEvent> eweList = getEventsInRangeWithCategory(//eweRepository.findByCityIdAndCategoryAndDateStartBetweenOrderByDateStart(
                    cityId, category, startTimeInterval, endTimeInterval
            );
            eweListsByCategory.put(category, eweList);
        }

        return cleanExtremeWeatherEventDuplicates(cityId, eweListsByCategory);
    }


    /**
     * Identifies and merges overlapping extreme weather events (EWEs) for a given city across all categories.
     * <p>
     * For each category of extreme weather event, the provided list of events is analyzed to detect overlaps.
     * Overlapping events are merged into a single event by:
     * <ul>
     *   <li>Using the earliest start date and the latest end date (unless any end date is null, in which case it remains null)</li>
     *   <li>Assigning the maximum strength value among the overlapping events</li>
     * </ul>
     * All original overlapping events are removed from the repository, and the resulting merged events are saved.
     *
     * @param cityId the identifier of the city to which the events belong
     * @param eweListsByCategory a map associating each {@link ExtremeWeatherEventCategory} with its corresponding list of events to process
     * @return a map containing the number of EWEs removed and inserted, with keys {@code removed} and {@code inserted}
     */
    private Map<String, Integer> cleanExtremeWeatherEventDuplicates(
            String cityId,
            Map<ExtremeWeatherEventCategory, List<ExtremeWeatherEvent>> eweListsByCategory
    ) {
        // Counters for removed and inserted EWEs
        int removedCount = 0;
        int insertedCount = 0;

        // Loop over each EWE category and its corresponding list
        for (Map.Entry<ExtremeWeatherEventCategory, List<ExtremeWeatherEvent>> entry : eweListsByCategory.entrySet()) {

            // Get all EWE for that category over given time interval, ordered by startTime
            ExtremeWeatherEventCategory eweCategory = entry.getKey();
            List<ExtremeWeatherEvent> eweList = entry.getValue();

            if (eweList == null || eweList.size() <= 1)
                continue;

            List<ExtremeWeatherEvent> eweToRemove = new ArrayList<>();
            List<ExtremeWeatherEvent> eweToInsert = new ArrayList<>();

            ExtremeWeatherEvent mergeEwe = eweList.get(0);
            int overlappingCounter = 1;

            // Loop over all events
            for (int i = 1; i < eweList.size(); i++) {

                ExtremeWeatherEvent currentEwe = eweList.get(i);

                // If dateStart is null then we have no way to check if overlapping
                if(currentEwe.getDateStart() == null) continue;

                // Check if the current ewe is overlapping with the previous / merge ewe
                // If they are overlapping
                if(areOrderedEwesOverlapping(mergeEwe, currentEwe)) {
                    // Increase the counter
                    overlappingCounter++;

                    // Set the strength to the max of margeEwe's and currentEwe's
                    mergeEwe.setStrength(Math.max(mergeEwe.getStrength(), currentEwe.getStrength()));

                    // If either is set to null then null is set
                    if (mergeEwe.getDateEnd() == null || currentEwe.getDateEnd() == null) {
                        mergeEwe.setDateEnd(null);
                    }
                    // Set the dateEnd to the max of margeEwe's and currentEwe's.
                    else {
                        mergeEwe.setDateEnd(mergeEwe.getDateEnd().isAfter(currentEwe.getDateEnd())
                                ? mergeEwe.getDateEnd()
                                : currentEwe.getDateEnd()
                        );
                    }

                }

                // If they are not overlapping
                else {
                    // We passed some overlapping ewe, they are to be marked as toRemove and the mergeEwe to be inserted
                    if(overlappingCounter != 1) {

                        // Insert all overlapping EWEs into the eweToRemove array
                        for (int j = i - overlappingCounter; j < i; j++)
                            eweToRemove.add(eweList.get(j));

                        // insert mergeEwe into eweToInsert array
                        eweToInsert.add(mergeEwe);

                        // Update counters
                        removedCount += overlappingCounter;
                        insertedCount++;
                    }

                    // Overwrite the mergeEwe
                    mergeEwe = currentEwe;

                    // Reset the counter
                    overlappingCounter = 1;
                }
            }

            // Handle eventual remaining overlapping EWEs
            if(overlappingCounter != 1) {

                // Insert all overlapping EWEs into the eweToRemove array
                for (int j = eweList.size() - overlappingCounter; j < eweList.size(); j++)
                    eweToRemove.add(eweList.get(j));

                // insert mergeEwe into eweToInsert array
                eweToInsert.add(mergeEwe);

                // Update counters
                removedCount += overlappingCounter;
                insertedCount++;
            }

            //eweRepository.deleteAll(eweToRemove);
            //eweRepository.saveAll(eweToInsert);
            City city = cityRepository.findById(cityId)
                    .orElseThrow(() -> new RuntimeException("City not found"));

            city.getEweList().removeIf(eweToRemove::contains);

            for (ExtremeWeatherEvent newEwe : eweToInsert) {
                if (newEwe.getId() == null) {
                    newEwe.setId(UUID.randomUUID().toString());
                }
            }

            city.getEweList().addAll(eweToInsert);

            cityRepository.save(city);
        }

        // Return the counts of removed and inserted EWEs in a Map
        Map<String, Integer> result = new HashMap<>();
        result.put("removed", removedCount);
        result.put("inserted", insertedCount);
        return result;
    }


    /**
     * Creates a new extreme weather event (EWE) based on the provided city and EWE information.
     * @param city City model
     * @param eweInfo Information on the EWE to be inserted
     * @param terminated {@code true} if the EWE has terminated, thus it has an End Date. {@code false} otherwise.
     * @return the newly created {@link ExtremeWeatherEvent}
     */
    private ExtremeWeatherEvent createNewEWE(
            City city,
            QuadrupleEWEInformationHolder eweInfo,
            Boolean terminated
    ){
        ExtremeWeatherEvent ewe = new ExtremeWeatherEvent();
        ewe.setCategory(eweInfo.getCategory());
        ewe.setStrength(eweInfo.getStrength());

        // Set start date
        ewe.setDateStart(eweInfo.getDateStart().toInstant().atZone(ZoneId.of("UTC")).toLocalDateTime());

        // Set end date only on terminated ewe
        if(terminated){
            ewe.setDateEnd(eweInfo.getDateEnd().toInstant().atZone(ZoneId.of("UTC")).toLocalDateTime());
        }

        return ewe;
    }

    /**
     * Retrieves and deletes all ongoing extreme weather events (EWEs) for a specified city
     * that started on or before the provided date. The remaining information is mapped
     * into a list of {@link QuadrupleEWEInformationHolder} objects for further processing.
     *
     * @param city the city for which to retrieve and delete ongoing EWEs
     * @param dateStart the upper bound date for selecting ongoing events based on their start date
     * @return a list of {@link QuadrupleEWEInformationHolder} representing the ongoing EWEs
     */
    private List<QuadrupleEWEInformationHolder> getListOfCurrentLocalEWEs(City city, LocalDateTime dateStart){

        List<QuadrupleEWEInformationHolder> currentLocalEWEs = new ArrayList<>();

        // Searches the ongoing EWEs in the db started after the given startDate
        List<ExtremeWeatherEvent> ongoingLocalEWEs = getEventsOngoingAt(city.getId(), dateStart);//eweRepository.findByCityIdAndDateEndIsNullAndDateStartLessThanEqual(city.getId(), dateStart);

        city.getEweList().removeIf(ongoingLocalEWEs::contains);

        cityRepository.save(city);

        // Map each EWE of ongoingLocalEWEs into a QuadrupleEWEInformationHolder
        return ongoingLocalEWEs.stream()
                .map(ewe -> new QuadrupleEWEInformationHolder(
                        ewe.getCategory(),
                        ewe.getStrength(),
                        Date.from(ewe.getDateStart().atZone(ZoneId.systemDefault()).toInstant()),
                        null
                ))
                .collect(Collectors.toList());
    }

    /**
     * Checks if two ExtremeWeatherEvent already ordered by startDate are overlapping in time, by checking EweB endDate against EweA dates.
     * dateEnd set to null is intended as ongoing ExtremeWeatherEvent and treated as such.
     *
     * @param EweA First ExtremeWeatherEvent
     * @param EweB Second ExtremeWeatherEvent
     * @return Returns {@code true} if the two EWEs are overlapping, {@code false} otherwise
     */
    private Boolean areOrderedEwesOverlapping(ExtremeWeatherEvent EweA, ExtremeWeatherEvent EweB) {

        // If EweA is still ongoing then the two EweB are necessarily overlapping
        if(EweA.getDateEnd() == null){
            return true;
        }

        // Given that they're already order by startDate we can check if they are overlapping by
        // checking if EweB startDate is before EweA endDate
        return !EweB.getDateStart().isAfter(EweA.getDateEnd());
    }
}
