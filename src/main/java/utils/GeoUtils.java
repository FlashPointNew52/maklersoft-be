package utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.LinkedList;
import java.util.List;

public class GeoUtils {

    static Logger logger = LoggerFactory.getLogger(GeoUtils.class);

    public static Double[] getCoordsByAddr(String address) {
        Double[] coords = null;
        try {
            String url = "https://geocode-maps.yandex.ru/1.x/?geocode=" + URLEncoder.encode( address, "UTF-8").toString() + "&format=json&apikey=2902fd2b-044e-4aad-b52a-911b742bcabf";

            URL iurl = new URL(url);
            HttpURLConnection uc = (HttpURLConnection) iurl.openConnection();
            uc.connect();
            int status = uc.getResponseCode();
            String jsonStr;
            switch (status) {
                case 200:
                case 201:

                    BufferedReader br = new BufferedReader(new InputStreamReader(uc.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line+"\n");
                    }
                    br.close();
                    jsonStr = sb.toString();

                    JsonObject jsonObject = new JsonParser().parse(jsonStr).getAsJsonObject();

                    // ->{response}->{GeoObjectCollection}->{metaDataProperty}->{GeocoderResponseMetaData}->{found}
                    Integer fCount = jsonObject.get("response").getAsJsonObject().get("GeoObjectCollection").getAsJsonObject()
                            .get("metaDataProperty").getAsJsonObject().get("GeocoderResponseMetaData").getAsJsonObject().get("found").getAsInt();

                    if (fCount > 0) {
                        // my @pos = split / /, $res->{response}->{GeoObjectCollection}->{featureMember}->[0]->{GeoObject}->{Point}->{pos};
                        String pos = jsonObject.get("response").getAsJsonObject().get("GeoObjectCollection").getAsJsonObject().get("featureMember").getAsJsonArray().get(0).getAsJsonObject().get("GeoObject").getAsJsonObject().get("Point").getAsJsonObject().get("pos").getAsString();

                        String[] t = pos.split(" ");
                        coords = new Double[2];
                        coords[0] = Double.parseDouble(t[1]);
                        coords[1] = Double.parseDouble(t[0]);
                    }
                    break;
                case 400:
                    
            }

        } catch (Exception ex) {
            return null;
        }

        return coords;
    }

    public static List<String> getLocationDistrict(Double lat, Double lon) {
        String url = "https://geocode-maps.yandex.ru/1.x/?geocode=" + lon.toString() + "," + lat.toString() + "&format=json&apikey=2902fd2b-044e-4aad-b52a-911b742bcabf";
        List<String> dList = new LinkedList<>();

        try {
            URL iurl = new URL(url);
            HttpURLConnection uc = (HttpURLConnection) iurl.openConnection();

            uc.connect();

            int status = uc.getResponseCode();

            switch (status) {
                case 200:
                case 201:
                    String jsonStr;
                    BufferedReader br = new BufferedReader(new InputStreamReader(uc.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line+"\n");
                    }
                    br.close();
                    jsonStr = sb.toString();

                    JsonObject jsonObject = new JsonParser().parse(jsonStr).getAsJsonObject();

                    Integer fCount = jsonObject.get("response").getAsJsonObject().get("GeoObjectCollection").getAsJsonObject()
                            .get("metaDataProperty").getAsJsonObject().get("GeocoderResponseMetaData").getAsJsonObject().get("found").getAsInt();

                    if (fCount > 0) {
                        JsonArray t = jsonObject.get("response").getAsJsonObject().get("GeoObjectCollection").getAsJsonObject().get("featureMember").getAsJsonArray();

                        t.forEach(je -> {
                            JsonObject geoObject = je.getAsJsonObject().get("GeoObject").getAsJsonObject();

                            String kind = geoObject.get("metaDataProperty").getAsJsonObject().get("GeocoderMetaData").getAsJsonObject().get("kind").getAsString();

                            if (kind.equals("district")) {
                                dList.add(geoObject.get("name").getAsString());
                            }
                        });
                    }
            }
        } catch(Exception ex) {
            logger.error(ex.getMessage());
        }

        return dList;
    }

    public void getLocationPois(Double lat, Double lon) {

    }
}
