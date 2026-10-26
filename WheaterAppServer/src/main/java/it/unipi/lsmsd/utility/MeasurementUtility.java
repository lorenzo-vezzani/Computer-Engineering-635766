package it.unipi.lsmsd.utility;

import it.unipi.lsmsd.model.HourlyMeasurement;
import it.unipi.lsmsd.model.MeasurementField;

public class MeasurementUtility {

    /**
     * Restituisce il valore del campo specificato da MeasurementField per l'oggetto HourlyMeasurement fornito.
     *
     * @param measurement Oggetto HourlyMeasurement da cui estrarre il valore
     * @param field Enum del campo da ottenere
     * @return Il valore Double del campo, oppure null se non presente
     * @throws IllegalArgumentException se il campo non è supportato
     */
    public static Double getMeasurementValue(HourlyMeasurement measurement, MeasurementField field) {
        return switch (field) {
            case RAINFALL -> measurement.getRainfall();
            case SNOWFALL -> measurement.getSnowfall();
            case WINDSPEED -> measurement.getWindSpeed();
            case TEMPERATURE -> measurement.getTemperature();
            default -> throw new IllegalArgumentException("Unsupported measurement field: " + field);
        };
    }

    /**
     * Restituisce il nome del campo nella classe HourlyMeasurement corrispondente al MeasurementField fornito.
     *
     * @param field Enum del campo
     * @return Nome del campo come stringa (es. "rainfall", "windSpeed", etc.)
     * @throws IllegalArgumentException se il campo non è supportato
     */
    public static String getFieldName(MeasurementField field) {
        return switch (field) {
            case RAINFALL -> "rainfall";
            case SNOWFALL -> "snowfall";
            case WINDSPEED -> "windSpeed";
            case TEMPERATURE -> "temperature";
            default -> throw new IllegalArgumentException("Unsupported measurement field: " + field);
        };
    }
}
