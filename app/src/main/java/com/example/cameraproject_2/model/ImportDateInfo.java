package com.example.cameraproject_2.model;
import com.google.gson.annotations.SerializedName;
public class ImportDateInfo {
    @SerializedName("date")
    private String date;

    @SerializedName("timezone_type")
    private int timezoneType;

    @SerializedName("timezone")
    private String timezone;

    // Getters and Setters (或 public fields, 但 Gson 可以處理 private + getters)
    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    public int getTimezoneType() { return timezoneType; }
    public void setTimezoneType(int timezoneType) { this.timezoneType = timezoneType; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
}