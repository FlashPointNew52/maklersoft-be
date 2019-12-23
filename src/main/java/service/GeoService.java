package service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import configuration.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.GeoUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.*;

public class GeoService {
    Logger logger = LoggerFactory.getLogger(OfferService.class);

    // return lat, lon
    public Double[] getLocation(String address) {

        return GeoUtils.getCoordsByAddr(address);
    }

    public List<String> getDistrict(Double lat, Double lon) {

        return GeoUtils.getLocationDistrict(lat, lon);
    }

    public void getPois(Double lat, Double lon) {

    }

    public JsonObject getKladrData(String query, String contentType, Integer withParent, String parent) throws UnsupportedEncodingException {
        String parentParam = "";
        String url =  "http://kladr-api.ru/api.php?"
                + "query=" + URLEncoder.encode( query, "UTF-8")
                + "&contentType=" + contentType
                + "&withParent=" + withParent
                + "&limit=10&token=3dD8k2K472BRGnRriAby9yYY6hhhTZbQ";
        if(parent != null){
            switch(contentType){
                case "street": parentParam = "cityId=" + parent; break;
                case "building": parentParam = "streetId=" + parent; break;
                //case "building": parentParam = "streetId=" + parent; break;
            }
            url += "&" + parentParam;
        }

        this.logger.info(url);
        URL iurl = null;
        try {
            iurl = new URL(url);
            HttpURLConnection uc = (HttpURLConnection) iurl.openConnection();
            uc.connect();
            int status = uc.getResponseCode();
            switch (status) {
                case 200:
                case 201:
                    String jsonStr;
                    BufferedReader br = new BufferedReader(new InputStreamReader(uc.getInputStream(), Charset.forName("UTF-8")));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line + "\n");
                    }
                    br.close();
                    jsonStr = sb.toString();
                    JsonObject jsonObject = new JsonParser().parse(jsonStr).getAsJsonObject();
                    return jsonObject;
            }

        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
