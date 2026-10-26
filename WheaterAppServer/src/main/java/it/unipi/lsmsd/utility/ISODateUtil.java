package it.unipi.lsmsd.utility;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;

// Provides ISO Conversion methods
// Utility class so cannot be instantiated
public final class ISODateUtil {
    
    // Private constructor to prevent instantiation
    private ISODateUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    // Helper method for mapHourlyMeasurement() : returns ISO Date for given time
    public static Date getISODate(String time){
        // Parse the time string (without timezone)
        DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        LocalDateTime localTime = LocalDateTime.parse(time, inputFormatter);
        // Convert LocalDateTime to Instant in UTC
        Instant instant = localTime.toInstant(ZoneOffset.UTC);
        // Convert Instant to java.util.Date
        return Date.from(instant);
    }
}
