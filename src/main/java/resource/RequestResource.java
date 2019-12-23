package resource;

import java.util.*;

import static spark.Spark.get;
import static spark.Spark.post;
import static spark.Spark.put;

import auxclass.ErrorMsg;
import auxclass.UploadFile;
import com.google.gson.Gson;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.elasticsearch.common.geo.GeoPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import configuration.AppConfig;
import service.RequestService;
import entity.Request;
import entity.User;
import utils.CommonUtils;

public class RequestResource {

    Logger logger = LoggerFactory.getLogger(RequestResource.class);
    Gson gson = new Gson();

    private final RequestService requestService;


    public RequestResource(RequestService requestService) {
        this.requestService = requestService;
        setupEndpoints();
    }

    private void setupEndpoints() {

        get(AppConfig.API_CONTEXT + "/request/list", "application/json", (request, response) -> {

            Map<String, Object> result = new HashMap<>();

            int page = 0;
            int perPage = 32;

            String searchQuery = "";
            Map<String, Object> filters = new HashMap<>();
            Map<String, String> sort = new HashMap<>();
            GeoPoint[] polygon = new GeoPoint[0];

            User us = request.session().attribute("user");
            Long accountId = us.getAccountId();
            Long userId = us.getId();

            if (request.queryParams("accountId") != null) {
                accountId = Long.parseLong(request.queryParams("accountId"));
            }

            if (request.queryParams("userId") != null) {
                userId = Long.parseLong(request.queryParams("userId"));
            }

            if (request.queryParams("page") != null) {
                page = Integer.parseInt(request.queryParams("page"));
            }
            if (request.queryParams("per_page") != null) {
                perPage = Integer.parseInt(request.queryParams("per_page"));
            }

            if (request.queryParams("filter") != null) {
                String filtStr = request.queryParams("filter");
                filters = CommonUtils.JsonToObjMap(filtStr);
            }
            if (request.queryParams("sort") != null) {
                String sortStr = request.queryParams("sort");
                sort = CommonUtils.JsonToMap(sortStr);
            }
            if (request.queryParams("search_area") != null) {
                String polygonStr = request.queryParams("search_area");
                polygon = gson.fromJson(polygonStr, GeoPoint[].class);
            }

            if (request.queryParams("search_query") != null) {
                searchQuery = request.queryParams("search_query");
            }

            RequestService.ListResult r;
            r = requestService.list(accountId, userId, page, perPage, filters, sort, searchQuery, Arrays.asList(polygon));
            result.put("response", "ok");
            result.put("result", r);

            return result;
        }, gson::toJson);

        get(AppConfig.API_CONTEXT + "/request/list_for_offer/:id", "application/json", (request, response) -> {
            Map<String, Object> result = new HashMap<>();

            Long accountId = 0L;
            int page = 0;
            int perPage = 32;


            long offerId = Long.parseLong(request.params(":id"));

            if (request.queryParams("accountId") != null) {
                accountId = Long.parseLong(request.queryParams("accountId"));
            }

            if (request.queryParams("page") != null) {
                page = Integer.parseInt(request.queryParams("page"));
            }
            if (request.queryParams("per_page") != null) {
                perPage = Integer.parseInt(request.queryParams("per_page"));
            }


            List<Request> requestList = requestService.listForOffer(accountId, page, perPage, offerId);

            result.put("response", "ok");
            result.put("result", requestList);

            return result;
        }, gson::toJson);

        get(AppConfig.API_CONTEXT + "/request/get/:id", "application/json", (request, response) -> {

            Map<String, Object> result = new HashMap<>();
            long id = Long.parseLong(request.params(":id"));
            Request _request = requestService.get(id);

            result.put("response", "ok");
            result.put("result", _request);

            return result;
        }, gson::toJson);

        post(AppConfig.API_CONTEXT + "/request/save", "application/json", (request, response) -> {
            Map<String, Object> result = new HashMap<>();

            User us = request.session().attribute("user");
            Long accountId = us.getAccountId();
            Long userId = us.getId();
            Request req = gson.fromJson(request.body(), Request.class);
            if(us.getAccountId().equals(req.getAccountId())){

                Request res = requestService.save(req);

                result.put("response", "ok");
                result.put("result", res);
                response.status(200);
            } else {
                result.put("response", "fail");
                response.status(400);
            }


            return result;
        }, gson::toJson);


        get(AppConfig.API_CONTEXT + "/request/delete/:id", "application/json", (request, response) -> {
            Map<String, Object> result = new HashMap<>();
            try{
                Request req = requestService.get(Long.parseLong(request.params(":id")));
                try{
                    ArrayList<UploadFile> files = req.getDocuments();
                    requestService.delete(request.params(":id"));
                    if(files != null) files.forEach(file -> file.delete());
                } catch (Exception exp){
                    ErrorMsg err = new ErrorMsg("001: System error | " + ExceptionUtils.getStackTrace(exp), "Ошибка при удалении заявки!",
                            "RequestError", 0);
                    response.status(400);
                    return gson.toJson(err);
                }

                result.put("response", "ok");
                response.status();
            } catch (Exception exp){

                ErrorMsg err = new ErrorMsg("001: System error | " + ExceptionUtils.getStackTrace(exp), "Заявка не найдена, возможно удалёна ранее",
                        "RequestError", 1);
                response.status(400);
                return gson.toJson(err);
            }
            return result;
        }, gson::toJson);

    }

}
