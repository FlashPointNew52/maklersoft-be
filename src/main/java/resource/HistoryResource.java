package resource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static spark.Spark.get;
import static spark.Spark.post;
import static spark.Spark.put;

import com.google.gson.Gson;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import configuration.AppConfig;
import service.HistoryService;
import entity.Person;
import entity.History;

public class HistoryResource {

    Logger logger = LoggerFactory.getLogger(PersonResource.class);
    Gson gson = new Gson();

    private final HistoryService historyService;


    public HistoryResource(HistoryService historyService) {

        this.historyService = historyService;
        setupEndpoints();

    }

    private void setupEndpoints() {

        get(AppConfig.API_CONTEXT + "/history/list", "application/json", (request, response) -> {

            Map<String, Object> result = new HashMap<>();

            int page = 0;
            int perPage = 32;
            Long objectId = null;
            String objecType = null;

            String pageStr = request.queryParams("page");
            if (pageStr != null && StringUtils.isNumeric(pageStr)) {
                page = Integer.parseInt(pageStr);
            }

            String perPageStr = request.queryParams("perPage");
            if (perPageStr != null && StringUtils.isNumeric(perPageStr)) {
                perPage = Integer.parseInt(perPageStr);
            }

            String objIdStr = request.queryParams("objId");
            if (objIdStr != null && StringUtils.isNumeric(objIdStr)) {
                objectId = Long.parseLong(objIdStr);
            }


            if (request.queryParams("objecType") != null) {
                objecType = request.queryParams("objecType");
            }


            List<History> historyList = historyService.list(page, perPage, objectId, objecType, null);

            result.put("response", "ok");
            result.put("result", historyList);

            return result;
        }, gson::toJson);

        get(AppConfig.API_CONTEXT + "/history/get/:id", "application/json", (request, response) -> {

            Map<String, Object> result = new HashMap<>();

            String personIdStr = request.params(":id");

            if (personIdStr != null && StringUtils.isNumeric(personIdStr)) {
                long id = Long.parseLong(personIdStr);
                History history = historyService.get(id);

                result.put("response", "ok");
                result.put("result", history);
            } else {
                result.put("response", "fail");
                result.put("result", "id is not numeric");
            }


            return result;
        }, gson::toJson);

        post(AppConfig.API_CONTEXT + "/history/save", "application/json", (request, response) -> {

            Map<String, Object> result = new HashMap<>();

            History history = gson.fromJson(request.body(), History.class);
            //List<String> errors = historyService.check(history);
            /*
            if (errors.size() == 0) {
                History res = historyService.save(history);

                result.put("response", "ok");
                result.put("result", res);
                response.status(201);
            } else {
                result.put("response", "fail");
                result.put("result", errors);
                // should be 400, but its problematic to process it later so fck it
                response.status(200);
            }*/

            return result;
        }, gson::toJson);


        post(AppConfig.API_CONTEXT + "/history/delete/:id", "application/json", (request, response) -> {

            Map<String, Object> result = new HashMap<>();

            String personIdStr = request.params(":id");

            if (personIdStr != null && StringUtils.isNumeric(personIdStr)) {
                long id = Long.parseLong(personIdStr);
                History history = historyService.delete(id);

                result.put("response", "ok");
                result.put("result", result);
            } else {
                result.put("response", "fail");
                result.put("result", "id is not numeric");
            }

            return result;
        }, gson::toJson);

    }

}
