package com.example.cameraproject_2.model;
import com.google.gson.annotations.SerializedName;

public class FareEntry {
    @SerializedName("_id")
    private int id;

    @SerializedName("_importdate")
    private ImportDateInfo importDate;

    @SerializedName("起站")
    private String fromStation;

    @SerializedName("訖站")
    private String toStation;

    @SerializedName("全票票價")
    private String fullFare;

    @SerializedName("敬老卡愛心卡愛心陪伴卡及新北市兒童優惠票價")
    private String concessionFare;

    @SerializedName("臺北市兒童優惠票價")
    private String taipeiChildFare;

    @SerializedName("距離")
    private String distance;

    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public ImportDateInfo getImportDate() { return importDate; }
    public void setImportDate(ImportDateInfo importDate) { this.importDate = importDate; }
    public String getFromStation() { return fromStation; }
    public void setFromStation(String fromStation) { this.fromStation = fromStation; }
    public String getToStation() { return toStation; }
    public void setToStation(String toStation) { this.toStation = toStation; }
    public String getFullFare() { return fullFare; }
    public void setFullFare(String fullFare) { this.fullFare = fullFare; }
    public String getConcessionFare() { return concessionFare; }
    public void setConcessionFare(String concessionFare) { this.concessionFare = concessionFare; }
    public String getTaipeiChildFare() { return taipeiChildFare; }
    public void setTaipeiChildFare(String taipeiChildFare) { this.taipeiChildFare = taipeiChildFare; }
    public String getDistance() { return distance; }
    public void setDistance(String distance) { this.distance = distance; }
}