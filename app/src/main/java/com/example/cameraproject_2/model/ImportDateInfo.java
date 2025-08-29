package com.example.cameraproject_2.model;
import com.google.gson.annotations.SerializedName;
public class ImportDateInfo {
    @SerializedName("date")
    private String date;

    @SerializedName("timezone_type")
    private int timezoneType;

    @SerializedName("timezone")
    private String timezone;

    // Room 需要一個無參構造函數 (如果使用 @Embedded 並且此類沒有其他複雜類型)

    public ImportDateInfo() {}

        // Getters and Setters
        public String getDate() {
            return date;
        }

        public void setDate(java.lang.String date) {
            this.date = date;
        }

        public int getTimezoneType() {
            return timezoneType;
        }

        public void setTimezoneType(int timezoneType) {
            this.timezoneType = timezoneType;
        }

        public String getTimezone() {
            return timezone;
        }

        public void setTimezone(java.lang.String timezone) {
            this.timezone = timezone;
        }

    }
