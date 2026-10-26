package it.unipi.lsmsd.converter;

import it.unipi.lsmsd.model.ExtremeWeatherEventCategory;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class StringToEWECategoryConverter implements Converter<String, ExtremeWeatherEventCategory> {

    // Designed to be able to pass Upper/lower case ExtremeWeatherEventCategory from user,
    // like Rainstorm, RAINSTORM, rainstorm, etc
    @Override
    public ExtremeWeatherEventCategory convert(String source) {
        try {
            return ExtremeWeatherEventCategory.valueOf(source.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid value for ExtremeWeatherEventCategory: " + source);
        }
    }
}
