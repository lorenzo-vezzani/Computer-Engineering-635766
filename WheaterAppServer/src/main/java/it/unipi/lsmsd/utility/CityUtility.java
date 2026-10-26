package it.unipi.lsmsd.utility;


import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import it.unipi.lsmsd.model.City;
import it.unipi.lsmsd.model.EWEThreshold;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.io.IOException;


public final class CityUtility {
    
    // Private constructor to prevent instantiation
    private CityUtility(){}

    // Custom cityId generation logic
    // Pisa,Tuscany, (43.690685, 10.452489) --> tus-pis-43.6907-10.4525
    // 23 characters long
    // Static function for reusuabilty
    public static String generateCityId(String name, String region, Double latitude, Double longitude) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        DecimalFormat df = new DecimalFormat("0.0000", symbols);  // Format coordinates to 4 decimal places
        // Generate code based on each inputs
        String latCode = df.format(latitude);
        String lonCode = df.format(longitude);
        String nameCode = name.substring(0, 3);
        String regionCode = (region.length() >= 3) ? region.substring(0, 3):region;
        return (regionCode + "-" + nameCode + "-" + latCode + "-" + lonCode).toLowerCase();
    }

    // Reads City Name from the text file stored in the resources
    public static List<String> loadCityNames() throws IOException {
        String filename = "cityList.txt";
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        try (InputStream inputStream = classLoader.getResourceAsStream(filename);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {

            return reader.lines()
                         .map(String::trim)
                         .filter(line -> !line.isEmpty())
                         .collect(Collectors.toList());
        }
    }

    public static boolean hasCityAllThresholdsFields(City city){

        // Check that the city has the eweThresholds field
        if (city == null || city.getEweThresholds() == null) {
            return false;
        }

        EWEThreshold thresholds = city.getEweThresholds();

        // Check that all fields are specified
        return  !Double.isNaN(thresholds.getRainfall()) &&
                !Double.isNaN(thresholds.getSnowfall()) &&
                !Double.isNaN(thresholds.getMaxTemperature()) &&
                !Double.isNaN(thresholds.getMinTemperature()) &&
                !Double.isNaN(thresholds.getWindSpeed());
    }
}
