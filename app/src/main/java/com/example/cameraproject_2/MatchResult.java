package com.example.cameraproject_2;

import android.os.Parcel;
import android.os.Parcelable;

public class MatchResult implements Parcelable {
    private String uri;
    private String location;
    private int matches; // 新增 matches 字段，用於儲存匹配數量

    // 現有的構造函數
    public MatchResult(String uri, String location) {
        this.uri = uri;
        this.location = location;
        this.matches = 0; // 默認值
    }

    // 新增構造函數，支持 ORBActivity 的調用
    public MatchResult(String uri, String location, int matches) {
        this.uri = uri;
        this.location = location;
        this.matches = matches;
    }

    // Parcelable 構造函數
    protected MatchResult(Parcel in) {
        uri = in.readString();
        location = in.readString();
        matches = in.readInt(); // 從 Parcel 讀取 matches
    }

    public static final Creator<MatchResult> CREATOR = new Creator<MatchResult>() {
        @Override
        public MatchResult createFromParcel(Parcel in) {
            return new MatchResult(in);
        }

        @Override
        public MatchResult[] newArray(int size) {
            return new MatchResult[size];
        }
    };

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(uri);
        dest.writeString(location);
        dest.writeInt(matches); // 將 matches 寫入 Parcel
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public String getUri() {
        return uri;
    }

    public String getLocation() {
        return location;
    }

    // 新增 getMatches 方法
    public int getMatches() {
        return matches;
    }
}