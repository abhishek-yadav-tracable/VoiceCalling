package org.example.voicecampaign.domain.model;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

@Embeddable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessHours {
    
    @Builder.Default
    private LocalTime startTime = LocalTime.of(9, 0);
    
    @Builder.Default
    private LocalTime endTime = LocalTime.of(18, 0);
    
    @Builder.Default
    private String timezone = "UTC";
    
    @Builder.Default
    private String allowedDays = "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY";
    
    public boolean isWithinBusinessHours() {
        // If start and end time span full day, allow 24/7 calling
        if (startTime.equals(LocalTime.MIDNIGHT) && endTime.equals(LocalTime.of(23, 59))) {
            return true;
        }
        
        // If allowedDays includes all 7 days and times span full day, allow 24/7
        if (allowedDays != null && allowedDays.contains("SUNDAY") && allowedDays.contains("SATURDAY")
                && startTime.equals(LocalTime.MIDNIGHT) && endTime.equals(LocalTime.of(23, 59))) {
            return true;
        }
        
        try {
            ZoneId zoneId = ZoneId.of(timezone != null ? timezone : "UTC");
            ZonedDateTime now = ZonedDateTime.now(zoneId);
            LocalTime currentTime = now.toLocalTime();
            DayOfWeek currentDay = now.getDayOfWeek();
            
            Set<DayOfWeek> days = parseAllowedDays();
            
            if (!days.contains(currentDay)) {
                return false;
            }
            
            // Handle overnight hours (e.g., 22:00 - 06:00)
            if (endTime.isBefore(startTime)) {
                // Overnight: valid if current time is after start OR before end
                return !currentTime.isBefore(startTime) || !currentTime.isAfter(endTime);
            }
            
            return !currentTime.isBefore(startTime) && !currentTime.isAfter(endTime);
        } catch (Exception e) {
            // If timezone is invalid, default to allowing calls (fail-open)
            return true;
        }
    }
    
    public static BusinessHours allDay() {
        return BusinessHours.builder()
                .startTime(LocalTime.MIDNIGHT)
                .endTime(LocalTime.of(23, 59))
                .timezone("UTC")
                .allowedDays("MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY")
                .build();
    }
    
    private Set<DayOfWeek> parseAllowedDays() {
        if (allowedDays == null || allowedDays.isEmpty()) {
            return Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, 
                         DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);
        }
        
        String[] dayStrings = allowedDays.split(",");
        Set<DayOfWeek> days = new HashSet<>();
        for (String day : dayStrings) {
            days.add(DayOfWeek.valueOf(day.trim().toUpperCase()));
        }
        return days;
    }
}
