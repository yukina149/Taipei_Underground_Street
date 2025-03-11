package com.example.cameraproject_2;

public class NavigationPoint {
    private int order;
    private float x;
    private float y;
    private float z;

    public NavigationPoint(int order, float x, float y, float z) {
        this.order = order;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public int getOrder() {
        return order;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public float getZ() {
        return z;
    }
}
