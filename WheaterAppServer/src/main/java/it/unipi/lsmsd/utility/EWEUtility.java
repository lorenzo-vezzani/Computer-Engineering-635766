package it.unipi.lsmsd.utility;

import it.unipi.lsmsd.model.EWEThreshold;
import it.unipi.lsmsd.model.ExtremeWeatherEventCategory;
import it.unipi.lsmsd.model.HourlyMeasurement;
import org.springframework.data.util.Pair;

import java.util.List;

public class EWEUtility {

    /**
     * Evaluates the current {@link HourlyMeasurement} against the specified {@link EWEThreshold}
     * values and returns a list of pairs, each representing an {@link ExtremeWeatherEventCategory}
     * and its corresponding severity level.
     * <p>
     * The severity level is computed for each category using dedicated logic encapsulated
     * in specific methods (e.g., {@code getRainstormStrength}, {@code getHeatwaveStrength}, etc.),
     * comparing actual measured values to the provided thresholds.
     * </p>
     *
     * @param measurement the current set of weather data (e.g., temperature, rainfall, wind speed)
     * @param thresholds the threshold values used to determine the severity of each event type
     * @return a list of {@link Pair} objects, where each pair consists of:
     *         <ul>
     *             <li>an {@code ExtremeWeatherEventCategory} representing the event type</li>
     *             <li>an {@code Integer} indicating the calculated severity level for that type</li>
     *         </ul>
     */
    public static List<Pair<ExtremeWeatherEventCategory, Integer>> getCurrentEWEs(HourlyMeasurement measurement, EWEThreshold thresholds){
        return List.of(
            Pair.of(ExtremeWeatherEventCategory.RAINSTORM, getRainstormStrength(measurement.getRainfall(), thresholds.getRainfall())),
            Pair.of(ExtremeWeatherEventCategory.SNOWSTORM, getSnowstormStrength(measurement.getSnowfall(), thresholds.getSnowfall())),
            Pair.of(ExtremeWeatherEventCategory.HURRICANE, getHurricaneStrength(measurement.getWindSpeed(), thresholds.getWindSpeed())),
            Pair.of(ExtremeWeatherEventCategory.HEATWAVE, getHeatwaveStrength(measurement.getTemperature(), thresholds.getMaxTemperature())),
            Pair.of(ExtremeWeatherEventCategory.COLDWAVE, getColdwaveStrength(measurement.getTemperature(), thresholds.getMinTemperature()))
        );
    }

    /**
     * Returns a list of {@link QuadrupleEWEInformationHolder} objects, one for each predefined
     * {@link ExtremeWeatherEventCategory}. Each entry in the list is initialized with:
     * <ul>
     *   <li>The respective category of extreme weather event</li>
     *   <li>A default counter value of {@code 0}</li>
     *   <li>{@code null} values for the remaining two fields, indicating absence of additional data</li>
     * </ul>
     *
     * This method is typically used as a base template to be populated later during
     * data processing or aggregation steps related to extreme weather events (EWE).
     *
     * @return an immutable list of placeholder {@code QuadrupleEWEInformationHolder} entries
     *         covering all supported EWE categories
     */
    public static List<QuadrupleEWEInformationHolder> getEmptyListOfEWEs(){
        return List.of(
            new QuadrupleEWEInformationHolder(ExtremeWeatherEventCategory.RAINSTORM, 0, null, null),
            new QuadrupleEWEInformationHolder(ExtremeWeatherEventCategory.SNOWSTORM, 0, null, null),
            new QuadrupleEWEInformationHolder(ExtremeWeatherEventCategory.HURRICANE, 0, null, null),
            new QuadrupleEWEInformationHolder(ExtremeWeatherEventCategory.HEATWAVE, 0, null, null),
            new QuadrupleEWEInformationHolder(ExtremeWeatherEventCategory.COLDWAVE, 0, null, null)
        );
    }

    /**
     * Calculates the strength of a rainstorm event based on the actual rainfall
     * and a predefined rainfall threshold.
     *
     * <p>If the rainfall is below the threshold, the strength is 0.
     * Otherwise, the strength is a proportional value calculated as the ratio
     * of rainfall to threshold, rounded up to the nearest integer.
     *
     * @param rainfall the amount of rainfall recorded (in mm)
     * @param rainfallThreshold the minimum rainfall to qualify as an event (in mm)
     * @return an integer strength value (0 if below threshold, ≥1 if equal or above)
     */
    public static int getRainstormStrength(Double rainfall, Double rainfallThreshold) {
        if (rainfall == null || rainfallThreshold == null || rainfall < rainfallThreshold) {
            return 0;
        }

        return (int) Math.ceil(rainfall / rainfallThreshold);
    }

    /**
     * Calculates the strength of a snowstorm event based on the actual snowfall
     * and a predefined snowfall threshold.
     *
     * <p>If the snowfall is below the threshold, the strength is 0.
     * Otherwise, the strength is a proportional value calculated as the ratio
     * of snowfall to threshold, rounded up to the nearest integer.
     *
     * @param snowfall the amount of snowfall recorded (in mm or cm, depending on dataset)
     * @param snowfallThreshold the minimum snowfall to qualify as an event
     * @return an integer strength value (0 if below threshold, ≥1 if equal or above)
     */
    public static int getSnowstormStrength(Double snowfall, Double snowfallThreshold) {
        if (snowfall == null || snowfallThreshold == null || snowfall < snowfallThreshold) {
            return 0;
        }

        return (int) Math.ceil(snowfall / snowfallThreshold);
    }

    /**
     * Calculates the strength of a hurricane event based on the actual wind speed
     * and a predefined wind speed threshold.
     *
     * <p>If the wind speed is below the threshold, the strength is 0.
     * Otherwise, the strength is a proportional value calculated as the ratio
     * of wind speed to threshold, rounded up to the nearest integer.
     *
     * @param windSpeed the wind speed recorded (in km/h, m/s, etc.)
     * @param windSpeedThreshold the minimum wind speed to qualify as a hurricane event
     * @return an integer strength value (0 if below threshold, ≥1 if equal or above)
     */
    public static int getHurricaneStrength(Double windSpeed, Double windSpeedThreshold) {
        if (windSpeed == null || windSpeedThreshold == null || windSpeed < windSpeedThreshold) {
            return 0;
        }

        return (int) Math.ceil(windSpeed / windSpeedThreshold);
    }

    /**
     * Calculates the strength of a heatwave event based on the actual temperature
     * and a predefined maximum temperature threshold.
     *
     * <p>If the temperature is below or equal to the threshold, the strength is 0.
     * Otherwise, the strength is a proportional value calculated as the ratio
     * of temperature to threshold, rounded up to the nearest integer.
     *
     * @param temperature the recorded temperature (in °C)
     * @param maxTempThreshold the minimum temperature to qualify as a heatwave event
     * @return an integer strength value (0 if below or equal to threshold, ≥1 if above)
     */
    public static int getHeatwaveStrength(Double temperature, Double maxTempThreshold) {
        if (temperature == null || maxTempThreshold == null || temperature <= maxTempThreshold) {
            return 0;
        }

        return (int) Math.ceil(temperature / maxTempThreshold);
    }

    /**
     * Calculates the strength of a coldwave event based on the actual temperature
     * and a predefined minimum temperature threshold.
     *
     * <p>If the temperature is above or equal to the threshold, the strength is 0.
     * Otherwise, the strength is a proportional value calculated as the ratio
     * of the threshold to the temperature, rounded up to the nearest integer.
     *
     * @param temperature the recorded temperature (in °C)
     * @param minTempThreshold the maximum temperature to qualify as a coldwave event
     * @return an integer strength value (0 if above or equal to threshold, ≥1 if below)
     */
    public static int getColdwaveStrength(Double temperature, Double minTempThreshold) {
        if (temperature == null || minTempThreshold == null || temperature >= minTempThreshold) {
            return 0;
        }

        return (int) Math.ceil(minTempThreshold / temperature);
    }
}
