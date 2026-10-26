package it.unipi.lsmsd.converter;

import it.unipi.lsmsd.model.MeasurementField;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class StringToMeasurementFieldConverter  implements Converter<String, MeasurementField> {

    @Override
    public MeasurementField convert(String source) {
        try {
            return MeasurementField.valueOf(source.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid value for ExtremeWeatherEventCategory: " + source);
        }
    }
}