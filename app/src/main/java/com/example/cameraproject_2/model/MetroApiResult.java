package com.example.cameraproject_2.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class MetroApiResult {
    @SerializedName("results")
    private List<FareEntry> fareEntries;

    @SerializedName("limit") //  API 會回傳 limit
    private int limit;

    @SerializedName("offset") //  API 會回傳 offset
    private int offset;

    @SerializedName("count") // <<<<< 添加這個欄位
    private int count;       // 用於存放總記錄數

    public List<FareEntry> getFareEntries() {
        return fareEntries;
    }

    public void setFareEntries(List<FareEntry> fareEntries) {
        this.fareEntries = fareEntries;
    }

    public int getLimit() { return limit; }
    public void setLimit(int limit) { this.limit = limit; }

    public int getOffset() { return offset; }
    public void setOffset(int offset) { this.offset = offset; }

    public int getCount() { // <<<<< 添加 getter
        return count;
    }
    public void setCount(int count) { // <<<<< 添加 setter (雖然 Gson 會直接賦值)
        this.count = count;
    }
}