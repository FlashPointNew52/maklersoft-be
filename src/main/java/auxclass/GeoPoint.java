package auxclass;

import java.io.Serializable;

public class GeoPoint implements Serializable {

    private static final long serialVersionUID = -6820733481585375980L;

    public Double lat;
    public Double lon;

    public GeoPoint() {
        this.lat = null;
        this.lon = null;
    }

    public GeoPoint(double lat, double lon) {
        this.lat = lat;
        this.lon = lon;
    }

    @Override
    public String toString() {
        return "{" +
                "\"lat\":" + lat +
                ", \"lon\":" + lon +
                '}';
    }

    public String getAsString(){
        return "" + lat + "," + lon;
    }
}
