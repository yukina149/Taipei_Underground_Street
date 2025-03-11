package com.example.cameraproject_2;

public class LocationData {
    private String locationName;
    private String imageData;
    private String imageFileName;

    public LocationData(String locationName, String imageData, String imageFileName) {
        this.locationName = locationName;
        this.imageData = imageData;
        this.imageFileName = imageFileName;
    }

    public String getLocationName() {
        return locationName;
    }

    public String getImageData() {
        return imageData;
    }

    public String getImageFileName() {
        return imageFileName;
    }
}
