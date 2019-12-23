package resource;

import java.util.*;

import static spark.Spark.get;
import static spark.Spark.post;
import static spark.Spark.put;

import com.google.gson.Gson;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import configuration.AppConfig;
import service.*;
import entity.User;
import auxclass.ErrorMsg;
import auxclass.UploadFile;
import utils.CommonUtils;

public class UserResource {

    Logger logger = LoggerFactory.getLogger(UserService.class);
    Gson gson = new Gson();

    private final UserService userService;
    private final OfferService offerService;
    private final RequestService requestService;
    private final PersonService personService;
    private final OrganisationService orgService;
    private final CommentService commentService;
    private final RatingService ratingService;

    public UserResource(UserService userService,
                        OfferService offerService,
                        RequestService requestService,
                        PersonService personService,
                        OrganisationService orgService,
                        CommentService commentService,
                        RatingService ratingService) {
        this.userService = userService;
        this.offerService = offerService;
        this.requestService = requestService;
        this.personService = personService;
        this.orgService = orgService;
        this.commentService = commentService;
        this.ratingService = ratingService;
        setupEndpoints();
    }

    private void setupEndpoints() {

        get(AppConfig.API_CONTEXT + "/user/list", "application/json", (request, response) -> {

            Map<String, Object> result = new HashMap<>();

            int page = 0;
            int perPage = 32;
            String searchQuery = "";
            Map<String, Object> filters = new HashMap<>();
            Map<String, String> sort = new HashMap<>();

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

            if (request.queryParams("searchQuery") != null) {
                searchQuery = request.queryParams("searchQuery");
            }

            if (request.queryParams("filter") != null) {
                String filterStr = request.queryParams("filter");
                filters = CommonUtils.JsonToObjMap(filterStr);
            }
            if (request.queryParams("sort") != null) {
                String sortStr = request.queryParams("sort");
                sort = CommonUtils.JsonToMap(sortStr);
            }

            filters.put("typeCode", "company");

            List<User> userList = userService.list(accountId, userId, page, perPage, filters, sort, searchQuery);

            result.put("response", "ok");
            result.put("result", userList);

            return result;
        }, gson::toJson);

        get(AppConfig.API_CONTEXT + "/user/get/:id", "application/json", (request, response) -> {

            Map<String, Object> result = new HashMap<>();

            String userIdStr = request.params(":id");
            if (userIdStr != null && StringUtils.isNumeric(userIdStr)) {
                long id = Long.parseLong(userIdStr);
                User user = userService.get(id);

                result.put("response", "ok");
                result.put("result", user);
            } else {
                result.put("response", "fail");
                result.put("result", "id is not numeric");
            }

            return result;
        }, gson::toJson);

        post(AppConfig.API_CONTEXT + "/user/save", "application/json", (request, response) -> {

            Map<String, Object> result = new HashMap<>();
            User user = gson.fromJson(request.body(), User.class);
            User us = request.session().attribute("user");
            Long accountId = us.getAccountId();
            // check user
            List<ErrorMsg> errors = userService.check(user);
            if (errors.size() == 0) {
                try{
                    User res = userService.save(user);
                    if(res.getId() != null) {
                        try{
                            this.offerService.updateOffers("ctx._source.agentName = \""+ CommonUtils.replaceSymb(res.getName()) + "\";",
                                    new HashMap<String, Object>() {{ put("accountId", accountId.toString()); put("agentId", res.getId().toString());}}
                            );
                        } catch (Exception exp){
                            errors.add(new ErrorMsg("900: System error | " + ExceptionUtils.getStackTrace(exp), "Ошибка при обновлении списка предложений", "OfferError", 0));
                        }

                        try{
                            this.requestService.updateRequest("ctx._source.agentName = \""+ CommonUtils.replaceSymb(res.getName()) + "\";",
                                    new HashMap<String, Object>() {{ put("accountId", accountId.toString()); put("agentId", res.getId().toString());}}
                            );
                            result.put("response", "ok");
                            result.put("result", res);
                            response.status(200);
                        } catch (Exception exp){
                            errors.add(new ErrorMsg("900: System error | " + ExceptionUtils.getStackTrace(exp), "Ошибка при обновлении списка заявок", "RequestError", 0));
                        }
                    }
                } catch(Exception exp){
                    errors.add(new ErrorMsg("900: System error | " + ExceptionUtils.getStackTrace(exp), "Ошибка при сохранении пользователя", "UserError", 0));
                }
            }

            if(errors.size() != 0){
                response.status(400);
                return errors.get(0);
            }

            return result;
        }, gson::toJson);

        get(AppConfig.API_CONTEXT + "/user/delete/:id", "application/json", (request, response) -> {
            Map<String, Object> result = new HashMap<>();
            try{
                User user = userService.get(Long.parseLong(request.params(":id")));
                List<ErrorMsg> errors = new LinkedList<ErrorMsg>();
                User us = request.session().attribute("user");
                Long accountId = us.getAccountId();
                try{
                    this.offerService.updateOffers("ctx._source.agentName = \"\"; ctx._source.agentId = null;",
                            new HashMap<String, Object>() {{ put("accountId", accountId.toString()); put("agentId", user.getId());}}
                    );
                } catch (Exception exp){
                    errors.add(new ErrorMsg("001: System error | " + ExceptionUtils.getStackTrace(exp), "Ошибка при обновлении списка предложений", "OfferError", 0));
                }
                try{
                    this.requestService.updateRequest("ctx._source.agentName = \"\"; ctx._source.agentId = null;",
                            new HashMap<String, Object>() {{ put("accountId", accountId.toString()); put("agentId", user.getId());}}
                    );
                } catch (Exception exp){
                    errors.add(new ErrorMsg("001: System error | " + ExceptionUtils.getStackTrace(exp), "Ошибка при обновлении списка заявок", "RequestError", 0));
                }
                try{
                    personService.updatePerson("ctx._source.agent = \"\";" + "ctx._source.agentId = null;",
                            new HashMap<String, Object>() {{ put("accountId", accountId.toString()); put("agentId", user.getId());}});
                } catch (Exception exp){
                    errors.add(new ErrorMsg("001: System error | " + ExceptionUtils.getStackTrace(exp), "Ошибка при обновлении связанных контактов!", "PersonError", 0));
                }
                try{
                    orgService.updateOrganisation("ctx._source.agent = \"\";" + "ctx._source.agentId = null;",
                            new HashMap<String, Object>() {{ put("accountId", accountId.toString()); put("agentId", user.getId());}});
                } catch (Exception exp){
                    errors.add(new ErrorMsg("001: System error | " + ExceptionUtils.getStackTrace(exp), "Ошибка при обновлении связанных организаций!", "OrgError", 0));
                }
                try{
                    userService.updateUser("ctx._source.agentId = null;",
                            new HashMap<String, Object>() {{ put("accountId", accountId.toString()); put("agentId", user.getId());}});
                } catch (Exception exp){
                    errors.add(new ErrorMsg("001: System error | " + ExceptionUtils.getStackTrace(exp), "Ошибка при обновлении связанных организаций!", "OrgError", 0));
                }
                try{
                    long res = ratingService.deleteByQuery(new HashMap<String, Object>(){{put("objType", "user"); put("objId", user.getId());}});
                    logger.info("deleted rating: " + res);
                } catch (Exception exp){
                    errors.add(new ErrorMsg("001: System error | " + ExceptionUtils.getStackTrace(exp), "Ошибка при очистке рейтинга!", "RatingError", 0));
                }
                try{
                    long res = ratingService.deleteByQuery(new HashMap<String, Object>(){{put("agentId", user.getId());}});
                    logger.info("deleted rating: " + res);
                } catch (Exception exp){
                    errors.add(new ErrorMsg("001: System error | " + ExceptionUtils.getStackTrace(exp), "Ошибка при очистке рейтинга!", "RatingError", 0));
                }
                try{
                    long res = commentService.deleteByQuery(new HashMap<String, Object>(){{put("objType", "user"); put("objId", user.getId());}});
                    logger.info("deleted comment: " + res);
                } catch (Exception exp){
                    errors.add(new ErrorMsg("001: System error | " + ExceptionUtils.getStackTrace(exp), "Ошибка удалении комментариев!", "CommentError", 0));
                }
                try{
                    long res = commentService.deleteByQuery(new HashMap<String, Object>(){{put("agentId", user.getId());}});
                    logger.info("deleted comment: " + res);
                } catch (Exception exp){
                    errors.add(new ErrorMsg("001: System error | " + ExceptionUtils.getStackTrace(exp), "Ошибка удалении комментариев!", "CommentError", 0));
                }
                try{
                    userService.delete(request.params(":id"));
                    UploadFile.deleteDirectory(AppConfig.FILE_STORAGE_PATH + "photo/users/" + user.getAccountId() + "/" + user.getId());
                } catch (Exception exp){
                    errors.add(new ErrorMsg("001: System error | " + ExceptionUtils.getStackTrace(exp), "Ошибка при удалении пользователя!",
                            "UserError", 0));
                }
                if (errors.size() > 0){
                    response.status(400);
                    return gson.toJson(errors.get(0));
                } else{
                    result.put("response", "ok");
                    response.status(200);
                }

            } catch (Exception exp){

                ErrorMsg err = new ErrorMsg("001: System error | " + ExceptionUtils.getStackTrace(exp), "Контакт не найден, возможно удалён ранее",
                        "OfferError", 1);
                response.status(400);
                return gson.toJson(err);
            }
            return result;
        }, gson::toJson);

    }

}
