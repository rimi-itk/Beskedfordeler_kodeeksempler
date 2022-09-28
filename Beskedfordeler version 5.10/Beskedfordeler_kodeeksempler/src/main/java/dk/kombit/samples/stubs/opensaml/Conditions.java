package dk.kombit.samples.stubs.opensaml;

import java.util.Calendar;
import java.util.Date;

public class Conditions {
	/**
     * Gets the date/time on, or after, which the assertion is invalid.
     * 
     * @return the date/time on, or after, which the assertion is invalid
     */
    public Date getNotOnOrAfter() {
    	Calendar cal = Calendar.getInstance();
    	cal.add(Calendar.MINUTE, 5);
    	return cal.getTime();
    }
}
