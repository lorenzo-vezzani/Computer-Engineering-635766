package it.unipi.lsmsd.utility;

import java.util.Date;
import it.unipi.lsmsd.model.ExtremeWeatherEventCategory;

public class QuadrupleEWEInformationHolder {

    private ExtremeWeatherEventCategory category;
    private Integer strength;
    private Date dateStart;
    private Date dateEnd;

    public QuadrupleEWEInformationHolder(ExtremeWeatherEventCategory category, Integer strength, Date dateStart, Date dateEnd) {
        this.category = category;
        this.strength = strength;
        this.dateStart = dateStart;
        this.dateEnd = dateEnd;
    }

    public void resetData(){
        this.setStrength(0);
        this.setDateStart(null);
        this.setDateEnd(null);
    }

    public ExtremeWeatherEventCategory getCategory() { return category; }
    public Integer getStrength() { return strength; }
    public Date getDateStart() { return dateStart; }
    public Date getDateEnd() { return dateEnd; }

    public void setCategory(ExtremeWeatherEventCategory category) { this.category = category; }
    public void setStrength(Integer strength) { this.strength = strength; }
    public void setDateStart(Date dateStart) { this.dateStart = dateStart; }
    public void setDateEnd(Date dateEnd) { this.dateEnd = dateEnd; }
}

