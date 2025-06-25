package com.example.cameraproject_2.model;

import com.google.gson.annotations.SerializedName;

public class MetroApiResponse {
    @SerializedName("result")
    private MetroApiResult result;

    public MetroApiResult getResult() {
        return result;
    }

    public void setResult(MetroApiResult result) {
        this.result = result;
    }
}