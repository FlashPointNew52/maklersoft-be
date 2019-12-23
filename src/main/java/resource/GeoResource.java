package resource;

import auxclass.AddressBlock;
import auxclass.ErrorMsg;
import auxclass.GeoPoint;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import configuration.AppConfig;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.GeoService;
import utils.GeoUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static spark.Spark.get;
import static spark.Spark.post;

public class GeoResource {
    Logger logger = LoggerFactory.getLogger(OfferResource.class);
    Gson gson = new Gson();

    private final GeoService geoService;


    public GeoResource(GeoService geoService) {
        this.geoService = geoService;
        setupEndpoints();
    }

    private void setupEndpoints() {

        get(AppConfig.API_CONTEXT + "/geo/code", "application/json", (request, response) -> {

            Map<String, Object> result = new HashMap<>();

            String address = request.queryParams("address");
            Double[] c = this.geoService.getLocation(address);

            if (c != null) {
                result.put("response", "ok");
                result.put("result", c);
            } else {
                result.put("response", "fail");
            }


            return result;
        }, gson::toJson);

        get(AppConfig.API_CONTEXT + "/geo/district", "application/json", (request, response) -> {

            Map<String, Object> result = new HashMap<>();

            Double lat = Double.parseDouble(request.queryParams("lat"));
            Double lon = Double.parseDouble(request.queryParams("lon"));
            List<String> dList = this.geoService.getDistrict(lat, lon);

            if (dList != null) {
                result.put("response", "ok");
                result.put("result", dList);
            } else {
                result.put("response", "fail");
            }


            return result;
        }, gson::toJson);

        get(AppConfig.API_CONTEXT + "/geo/fias", "application/json", (request, response) -> {

            Map<String, Object> result = new HashMap<>();

            try {
                JsonObject dList = this.geoService.getKladrData(request.queryParams("query"),
                        request.queryParams("contentType"),
                        Integer.parseInt(request.queryParams("withParent")),
                        request.queryParams("parent")
                );

                if (dList != null) {
                    result.put("response", "ok");
                    result.put("result", dList.getAsJsonObject());
                } else {
                    result.put("response", "fail");
                }
            } catch(Exception exp){
                ErrorMsg err = new ErrorMsg(ExceptionUtils.getStackTrace(exp), "Ошибка при получении данных от ФИАС!", "FiasError", 0);
                response.status(400);
                return err;
            }

            return result;
        }, gson::toJson);

        post(AppConfig.API_CONTEXT + "/geo/latLonWithArea", "application/json", (request, response) -> {
            Map<String, Object> result = new HashMap<>();

            AddressBlock addressBlock = gson.fromJson(request.body(), AddressBlock.class);
            String addressStr = addressBlock.getAsString();
            Double[] latLon = {0d, 0d};

            try{
                if (addressStr.length() > 0) {
                    latLon = GeoUtils.getCoordsByAddr(addressStr);
                    List<String> districts = GeoUtils.getLocationDistrict(latLon[0], latLon[1]);
                    if (!districts.isEmpty()) {
                        if (districts.size() > 1) {
                            addressBlock.setArea(districts.get(0));
                            addressBlock.setAdmArea(districts.get(1));
                        } else{
                            addressBlock.setAdmArea(districts.get(0));
                            addressBlock.setArea("");
                        }
                    }
                }
                result.put("response", "ok");
                result.put("addressBlock", addressBlock);
                result.put("latLon", new GeoPoint(latLon[0], latLon[1]));
            } catch(Exception exp){
                ErrorMsg err = new ErrorMsg(ExceptionUtils.getStackTrace(exp), "Ошибка при получении координат!", "FiasError", 1);
                response.status(400);
                return err;
            }

            return result;
        }, gson::toJson);
    }
}
