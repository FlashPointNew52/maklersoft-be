package resource;

import java.util.*;

import static spark.Spark.get;
import static spark.Spark.post;
import static spark.Spark.put;

import com.google.gson.Gson;
import org.elasticsearch.common.geo.GeoPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import configuration.AppConfig;
import service.OfferService;
import entity.Offer;
import entity.User;
import utils.CommonUtils;

public class OfferResource {

    Logger logger = LoggerFactory.getLogger(OfferResource.class);
    Gson gson = new Gson();

    private final OfferService offerService;


    public OfferResource(OfferService offerService) {
        this.offerService = offerService;
        setupEndpoints();
    }

    private void setupEndpoints() {

        get(AppConfig.API_CONTEXT + "/offer/list", "application/json", (request, response) -> {

            Map<String, Object> result = new HashMap<>();

            int page = 0;
            int perPage = 32;
            String source = "local";
            String searchQuery = "";
            Map<String, String> filters = new HashMap<>();
            Map<String, String> sort = new HashMap<>();
            GeoPoint[] polygon = new GeoPoint[0];

            User us = request.session().attribute("user");
            Long accountId = us.getAccountId();
            Long userId = us.getId();

            if (request.queryParams("accountId") != null) {
                accountId = Long.parseLong(request.queryParams("accountId"));
            }
            if (request.queryParams("page") != null) {
                page = Integer.parseInt(request.queryParams("page"));
            }
            if (request.queryParams("per_page") != null) {
                perPage = Integer.parseInt(request.queryParams("per_page"));
            }

            if (request.queryParams("source") != null) {
                source = request.queryParams("source");
            }

            if (request.queryParams("filter") != null) {
                String filterStr = request.queryParams("filter");
                filters = CommonUtils.JsonToMap(filterStr);
            }
            if (request.queryParams("sort") != null) {
                String sortStr = request.queryParams("sort");
                sort = CommonUtils.JsonToMap(sortStr);
            }

            if (request.queryParams("search_query") != null) {
                searchQuery = request.queryParams("search_query");
            }
            if (request.queryParams("search_area") != null) {
                String polygonStr = request.queryParams("search_area");
                polygon = gson.fromJson(polygonStr, GeoPoint[].class);
            }


            OfferService.ListResult r;
            if (source != null && source.equals("local")) {
                r = offerService.list(accountId, userId, page, perPage, filters, sort, searchQuery, Arrays.asList(polygon));
            } else {
                r = offerService.listImport(accountId, userId, page, perPage, filters, sort, searchQuery, Arrays.asList(polygon));
            }

            result.put("response", "ok");
            result.put("result", r);

            return result;
        }, gson::toJson);

        get(AppConfig.API_CONTEXT + "/offer/list_similar/:id", "application/json", (request, response) -> {
            Map<String, Object> result = new HashMap<>();

            long id = Long.parseLong(request.params(":id"));

            Long accountId = 0L;
            int page = 0;
            int perPage = 32;

            if (request.queryParams("accountId") != null) {
                accountId = Long.parseLong(request.queryParams("accountId"));
            }
            if (request.queryParams("page") != null) {
                page = Integer.parseInt(request.queryParams("page"));
            }
            if (request.queryParams("per_page") != null) {
                perPage = Integer.parseInt(request.queryParams("per_page"));
            }

            OfferService.ListResult r;
            r = offerService.listSimilar(accountId, page, perPage, id);

            result.put("response", "ok");
            result.put("result", r);

            return result;
        }, gson::toJson);

        get(AppConfig.API_CONTEXT + "/offer/get/:id", "application/json", (request, response) -> {

            Map<String, Object> result = new HashMap<>();
            long id = Long.parseLong(request.params(":id"));
            Offer offer = offerService.get(id);

            if (offer != null) {
                result.put("response", "ok");
                result.put("result", offer);
            } else {
                result.put("response", "not found");
            }

            return result;
        }, gson::toJson);

        post(AppConfig.API_CONTEXT + "/offer/save", "application/json", (request, response) -> {
            Map<String, Object> result = new HashMap<>();

            Offer offer = gson.fromJson(request.body(), Offer.class);
            Offer res = offerService.save(offer);

            result.put("response", "ok");
            result.put("result", res);
            response.status(202);

            return result;
        }, gson::toJson);


        post(AppConfig.API_CONTEXT + "/offer/delete/:id", "application/json", (request, response) -> {

            Map<String, Object> result = new HashMap<>();
            String id = offerService.delete(request.params(":id"));

            result.put("response", "ok");
            result.put("result", id);

            return result;
        }, gson::toJson);

    }

}
