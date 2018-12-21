package com.romif.hackair.widget;

import android.location.Location;
import android.os.Parcel;
import android.os.Parcelable;

public class LocationDto implements Parcelable {

    private double longitude;
    private double latitude;
    private String address;
    @SuppressWarnings("unused")
    public static final Parcelable.Creator<LocationDto> CREATOR = new Parcelable.Creator<LocationDto>() {
        @Override
        public LocationDto createFromParcel(Parcel in) {
            return new LocationDto(in);
        }

        @Override
        public LocationDto[] newArray(int size) {
            return new LocationDto[size];
        }
    };
    private String senseBoxId;
    private String sensorId;

    public LocationDto(double longitude, double latitude, String address) {
        this.longitude = longitude;
        this.latitude = latitude;
        this.address = address;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public LocationDto() {
    }

    protected LocationDto(Parcel in) {
        longitude = in.readDouble();
        latitude = in.readDouble();
        address = in.readString();
        senseBoxId = in.readString();
        sensorId = in.readString();
    }

    public String getSenseBoxId() {
        return senseBoxId;
    }

    public void setSenseBoxId(String senseBoxId) {
        this.senseBoxId = senseBoxId;
    }

    @Override
    public String toString() {
        return address;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        LocationDto that = (LocationDto) o;

        if (Double.compare(that.longitude, longitude) != 0) return false;
        return Double.compare(that.latitude, latitude) == 0;
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        temp = Double.doubleToLongBits(longitude);
        result = (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(latitude);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        return result;
    }

    public double distanceTo(Location to) {
        Location location = new Location("");
        location.setLatitude(getLatitude());
        location.setLongitude(getLongitude());
        return location.distanceTo(to);
    }

    public String getSensorId() {
        return sensorId;
    }

    public void setSensorId(String sensorId) {
        this.sensorId = sensorId;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeDouble(longitude);
        dest.writeDouble(latitude);
        dest.writeString(address);
        dest.writeString(senseBoxId);
        dest.writeString(sensorId);
    }
}