package com.example.cameraproject_2.model;
import com.google.gson.annotations.SerializedName;
import androidx.room.ColumnInfo;
import androidx.room.Embedded; // 用於 ImportDateInfo
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters; // 如果使用 TypeConverter

@Entity(tableName = "fare_entries") // 定義資料表名稱
public class FareEntry {

    @PrimaryKey // Room 需要主鍵
    @SerializedName("_id")
    private int id; // 這個 id 應該是唯一的，來自 API

    @Embedded // 將 ImportDateInfo 的欄位嵌入到 FareEntry 表中
    @SerializedName("_importdate")
    private ImportDateInfo importDate;

    @ColumnInfo(name = "from_station") // 可以為欄位指定名稱
    @SerializedName("起站")
    private String fromStation;

    @ColumnInfo(name = "to_station")
    @SerializedName("訖站")
    private String toStation;

    @ColumnInfo(name = "full_fare")
    @SerializedName("全票票價")
    private String fullFare;

    //更改此欄位名稱
    @ColumnInfo(name = "concession_fare")
    @SerializedName("敬老卡愛心卡愛心陪伴卡及臺北市與新北市兒童")
    private String concessionFare;

    //被政府改掉了 沒這個欄位
   // @ColumnInfo(name = "taipei_child_fare")
    //@SerializedName("臺北市兒童優惠票價")
    //private String taipeiChildFare;


    @ColumnInfo(name = "distance")
    @SerializedName("距離")
    private String distance;
    public FareEntry() {}

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
    //用不到的欄位
    // public String getTaipeiChildFare() { return taipeiChildFare; }
    //public void setTaipeiChildFare(String taipeiChildFare) { this.taipeiChildFare = taipeiChildFare; }
    public String getDistance() { return distance; }
    public void setDistance(String distance) { this.distance = distance; }
}