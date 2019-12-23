package resource;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import configuration.AppConfig;
import entity.Comment;
import entity.Organisation;
import entity.Person;
import entity.User;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.CommentService;
import service.OrganisationService;
import service.PersonService;
import service.UserService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static spark.Spark.get;
import static spark.Spark.post;

public class CommentResource {
    Logger logger = LoggerFactory.getLogger(CommentResource.class);
    Gson gson = new Gson();

    private final CommentService commentService;
    private final PersonService personService;
    private final UserService userService;
    private final OrganisationService orgService;

    public CommentResource(CommentService commentService, PersonService personService, UserService userService, OrganisationService orgService) {

        this.commentService = commentService;
        this.personService = personService;
        this.userService = userService;
        this.orgService = orgService;
        setupEndpoints();

    }

    private void setupEndpoints() {

        get(AppConfig.API_CONTEXT + "/comment/list", "application/json", (request, response) -> {

            Map<String, Object> result = new HashMap<>();


            Long objId = null;
            String type = null;
            int page = 0;
            int perPage = 32;

            User us = request.session().attribute("user");
            Long accountId = us.getAccountId();
            Long userId = us.getId();


            String pageStr = request.queryParams("page");
            if (pageStr != null && StringUtils.isNumeric(pageStr)) {
                page = Integer.parseInt(pageStr);
            }

            String perPageStr = request.queryParams("perPage");
            if (perPageStr != null && StringUtils.isNumeric(perPageStr)) {
                perPage = Integer.parseInt(perPageStr);
            }

            if (request.queryParams("objId") != null) {
                objId = Long.parseLong(request.queryParams("objId"));
            }

            if (request.queryParams("type") != null) {
                type = request.queryParams("type");
            }

            if(userId == null || type == null){
                result.put("response", "error");
                result.put("result", "wrong data");
            } else {
                CommentService.ListResult r;
                List<String> phones = new ArrayList<>();
                if(type.equals("person")) {
                    Person person = new Person();
                    person = personService.get(objId);
                    if (person != null)
                        phones = person.getPhoneBlock().getAsList();
                } else if(type.equals("user")){
                    User user = new User();
                    user = userService.get(objId);
                    if (user != null)
                        phones = user.getPhoneBlock().getAsList();
                } else if(type.equals("organisation")){
                    Organisation org = orgService.get(objId);
                    if (org != null)
                        phones = org.getPhoneBlock().getAsList();
                }

                if(phones.size() <= 0){
                    result.put("response", "error");
                    result.put("result", "wrong data");
                } else{
                    r = commentService.list(accountId, page, perPage, phones, null, null, type);
                    result.put("response", "ok");
                    result.put("result", r);
                }
            }

            return result;
        }, gson::toJson);

        post(AppConfig.API_CONTEXT + "/comment/save", "application/json", (request, response) -> {

            Map<String, Object> result = new HashMap<>();
            List<String> phones = new ArrayList<>();
            Comment comment = gson.fromJson(request.body(), Comment.class);

            User us = request.session().attribute("user");
            comment.setAgentId(us.getId());

            if (comment.getObjId() != null && comment.getObjType() != null) {
                if (comment.getObjType().equals("person")) {
                    Person person = new Person();
                    person = personService.get(comment.getObjId());
                    if (person != null)
                        phones = person.getPhoneBlock().getAsList();
                } else if (comment.getObjType().equals("user")) {
                    User user = new User();
                    user = userService.get(comment.getObjId());

                    if (user != null)
                        phones = user.getPhoneBlock().getAsList();
                } else if (comment.getObjType().equals("organisation")) {
                    Organisation org = orgService.get(comment.getObjId());
                    if (org != null)
                        phones = org.getPhoneBlock().getAsList();
                }
                if(phones.size() != 0){
                    comment.setPhones(phones.toArray(new String[phones.size()]));
                    Comment res = commentService.save(comment);
                    result.put("response", "ok");
                    result.put("result", res);
                    response.status(201);
                } else{
                    result.put("response", "error");
                    result.put("result", "phones_not_found");
                }
            } else {
                result.put("response", "error");
                result.put("result", "wrong");
            }
            return result;
        }, gson::toJson);


        post(AppConfig.API_CONTEXT + "/comment/delete/:id", "application/json", (request, response) -> {

            Map<String, Object> result = new HashMap<>();
            String res = commentService.delete(request.params(":id"));

            result.put("response", "ok");
            result.put("result", res);

            return result;
        }, gson::toJson);

        post(AppConfig.API_CONTEXT + "/comment/get/:id", "application/json", (request, response) -> {

            Map<String, Object> result = new HashMap<>();
            Comment res = commentService.get(Long.parseLong(request.params(":id")));

            result.put("response", "ok");
            result.put("result", res);

            return result;
        }, gson::toJson);

        post(AppConfig.API_CONTEXT + "/comment/estimate", "application/json", (request, response) -> {

            Map<String, Object> result = new HashMap<>();

            JsonObject jsonObject = new JsonParser().parse(request.body()).getAsJsonObject();

            Boolean estimate = jsonObject.get("estimate") != null ? Boolean.parseBoolean(jsonObject.get("estimate").toString()) : null;
            Long comment_id = jsonObject.get("comment_id") != null ? Long.parseLong(jsonObject.get("comment_id").toString()) : null;
            Long user_id = jsonObject.get("user_id") != null ? Long.parseLong(jsonObject.get("user_id").toString()) : null;

            if(estimate == null || comment_id == null || user_id == null){
                result.put("response", "error");
                result.put("result", false);
            } else {
                result.put("result", commentService.estimate(comment_id, user_id, estimate));
                result.put("response", "ok");
            }


            return result;
        }, gson::toJson);

    }

}
