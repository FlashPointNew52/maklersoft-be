package resource;

import java.util.*;

import static spark.Spark.get;
import static spark.Spark.post;
import static spark.Spark.put;

import auxclass.ErrorMsg;
import auxclass.PhoneBlock;
import auxclass.Rating;
import auxclass.UploadFile;
import com.google.gson.Gson;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import configuration.AppConfig;
import service.*;
import entity.Person;
import entity.User;
import utils.*;

public class PersonResource {

    Logger logger = LoggerFactory.getLogger(PersonResource.class);
    Gson gson = new Gson();

    private final PersonService personService;
    private final UserService userService;
    private final OrganisationService orgService;
    private final OfferService offerService;
    private final RequestService requestService;
    private final CommentService commentService;
    private final RatingService ratingService;

    public PersonResource(PersonService personService,
                          UserService userService,
                          OrganisationService orgService,
                          OfferService offerService,
                          RequestService requestService,
                          RatingService ratingService,
                          CommentService commentService) {
        this.personService = personService;
        this.userService = userService;
        this.orgService = orgService;
        this.offerService = offerService;
        this.requestService = requestService;
        this.commentService = commentService;
        this.ratingService = ratingService;

        setupEndpoints();
    }

    private void setupEndpoints() {

        get(AppConfig.API_CONTEXT + "/person/list", "application/json", (request, response) -> {

            Map<String, Object> result = new HashMap<>();

            String source = "local";
            int page = 0;
            int perPage = 32;
            String searchQuery = "";
            Map<String, Object> filters = new HashMap<>();
            Map<String, String> sort = new HashMap<>();

            User us = request.session().attribute("user");
            Long accountId = us.getAccountId();
            Long userId = us.getId();

            if(accountId == null){
                result.put("response", "FAIL");
                result.put("result", "You account not found");
            }

            if (request.queryParams("source") != null) {
                source = request.queryParams("source");
            }

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

            List<Person> personList = new ArrayList<>();
            if(source.equals("local"))
                personList = personService.list(accountId, null, page, perPage, filters, sort, searchQuery);
            else {
                List<User> userList = new ArrayList<>();
                userList = userService.list(accountId, userId, page, perPage, filters, sort, searchQuery);
                for (User fuser: userList) {
                    Map<String, Object> newFlt = new HashMap<>();
                    newFlt.put("userRef", fuser.getId().toString());
                    if(fuser.getOrganisationId() != null){
                        fuser.setOrganisation(orgService.get(fuser.getOrganisationId()));
                    } else{
                        fuser.setOrganisationId(fuser.getAccountId());
                        fuser.setOrganisation(orgService.get(fuser.getAccountId()));
                    }
                    try{
                        Person pers = personService.list(accountId, null, 0, 1, newFlt, new HashMap<>(), "").get(0);
                        fuser.setPersRef(pers.getId());
                        fuser.setStateCode(pers.getStateCode());
                        fuser.setTypeCode(pers.getTypeCode());
                    } catch (Exception ex){
                        fuser.setPersRef(null);
                    }
                    if(fuser.getAccountId().equals(accountId))
                        fuser.setPersRef(accountId);
                    personList.add(fuser.toPerson());

                }
            }

            result.put("response", "ok");
            result.put("result", personList);

            return result;
        }, gson::toJson);

        get(AppConfig.API_CONTEXT + "/person/get/:id", "application/json", (request, response) -> {

            Map<String, Object> result = new HashMap<>();

            String personIdStr = request.params(":id");

            if (personIdStr != null && StringUtils.isNumeric(personIdStr)) {
                long id = Long.parseLong(personIdStr);

                Person person = personService.get(id);

                result.put("response", "ok");
                result.put("result", person);
            } else {
                result.put("response", "fail");
                result.put("result", "id is not numeric");
            }


            return result;
        }, gson::toJson);

        post(AppConfig.API_CONTEXT + "/person/save", "application/json", (request, response) -> {

            Map<String, Object> result = new HashMap<>();
            Person person = gson.fromJson(request.body(), Person.class);
            User us = request.session().attribute("user");
            Long accountId = us.getAccountId();
            Long userId = us.getId();
            person.setAccountId(accountId);
            List<ErrorMsg> errors = personService.check(person);

            if (errors.size() == 0) {
                User user = userService.getByPhone(person.getPhoneBlock().getAsList());
                if(user != null)
                    person.setUserRef(user.getId());
                else
                    person.setUserRef(null);
                if(person.getAgentId() != null)
                    person.setAgent(userService.get(person.getAgentId()));
                else
                    person.setAgent(null);
                if(person.getOrganisationId() == null && person.getOrganisation() != null)
                    person.setOrganisationId(person.getOrganisation().getId());
                if(person.getOrganisationId() != null && person.getOrganisation() == null){
                    person.setOrganisation(this.orgService.get(person.getOrganisationId()));
                }
                try{
                    Person res = personService.save(person);
                    if(person.getId() != null){
                        try{
                            this.offerService.updateOffers("ctx._source.contactType = \""+ person.getTypeCode() +"\";" +
                                            "ctx._source.contactName = \""+ CommonUtils.replaceSymb(person.getName()) + "\";" +
                                            "ctx._source.isMiddleman = \""+ (person.getIsMiddleman() ? "middleman": "owner") + "\";",
                                    new HashMap<String, Object>() {{ put("accountId", accountId.toString()); put("personId", person.getId().toString());}}
                            );
                        }catch (Exception exp){
                            errors.add(new ErrorMsg("900: System error | " + ExceptionUtils.getStackTrace(exp), "Ошибка при обновлении списка предложений", "OfferError", 0));
                        }
                        try{
                            this.requestService.updateRequest("ctx._source.contactType = \""+ person.getTypeCode() +"\";" +
                                            "ctx._source.contactName = \""+ CommonUtils.replaceSymb(person.getName()) + "\";" +
                                            "ctx._source.isMiddleman = \""+ (person.getIsMiddleman() ? "middleman": "owner") + "\";",
                                    new HashMap<String, Object>() {{ put("accountId", accountId.toString()); put("personId", person.getId().toString());}}
                            );
                            result.put("response", "ok");
                            result.put("result", res);
                            response.status(201);
                        }catch (Exception exp){
                            errors.add(new ErrorMsg("900: System error | " + ExceptionUtils.getStackTrace(exp), "Ошибка при обновлении списка заявок", "RequestError", 0));
                        }
                    }
                } catch(Exception exp){
                    errors.add(new ErrorMsg("900: System error | " + ExceptionUtils.getStackTrace(exp), "Ошибка при сохранении контакта", "PersonError", 0));
                }

            }
            if(errors.size() != 0){
                response.status(400);
                return  errors.get(0);
            }

            return result;
        }, gson::toJson);

        post(AppConfig.API_CONTEXT + "/person/findByPhone", "application/json", (request, response) -> {
            Map<String, Object> result = new HashMap<>();
            PhoneBlock phones = gson.fromJson(request.body(), PhoneBlock.class);
            User us = request.session().attribute("user");

            Person pers = personService.getByPhone(phones.getAsList(), us.getAccountId());
            result.put("response", "ok");
            result.put("result", pers);
            response.status(201);

            return result;

        }, gson::toJson);

        get(AppConfig.API_CONTEXT + "/person/delete/:id", "application/json", (request, response) -> {
            Map<String, Object> result = new HashMap<>();
            try{
                Person person = personService.get(Long.parseLong(request.params(":id")));
                List<ErrorMsg> errors = new LinkedList<ErrorMsg>();
                User us = request.session().attribute("user");
                Long accountId = us.getAccountId();
                try{
                    orgService.updateOrganisation("ctx._source.contactName = \"\";" + "ctx._source.contactId = null;",
                            new HashMap<String, Object>() {{ put("accountId", accountId.toString()); put("contactId", person.getId());}});
                } catch (Exception exp){
                    errors.add(new ErrorMsg("001: System error | " + ExceptionUtils.getStackTrace(exp), "Ошибка при обновлении связанных организаций!", "OrgError", 0));
                }
                try{
                    long res = offerService.deleteWithFiles(new HashMap<String, Object>(){{ put("accountId", accountId); put("personId", person.getId());}});
                    logger.info("deleted offer: " + res);
                } catch (Exception exp){
                    errors.add(new ErrorMsg("001: System error | " + ExceptionUtils.getStackTrace(exp), "Ошибка при удалении связанных объектов!", "OfferError", 0));
                }
                try{
                    long res = requestService.deleteWithFiles(new HashMap<String, Object>(){{ put("accountId", accountId); put("personId", person.getId());}});
                    logger.info("deleted request: " + res);
                } catch (Exception exp){
                    errors.add(new ErrorMsg("001: System error | " + ExceptionUtils.getStackTrace(exp), "Ошибка при удалении связанных объектов!", "OfferError", 0));
                }
                try{
                    long res = ratingService.deleteByQuery(new HashMap<String, Object>(){{put("objType", "person"); put("objId", person.getId());}});
                    logger.info("deleted rating: " + res);
                } catch (Exception exp){
                    errors.add(new ErrorMsg("001: System error | " + ExceptionUtils.getStackTrace(exp), "Ошибка при очистке рейтинга!", "RatingError", 0));
                }
                try{
                    long res = commentService.deleteByQuery(new HashMap<String, Object>(){{put("objType", "person"); put("objId", person.getId());}});
                    logger.info("deleted comment: " + res);
                } catch (Exception exp){
                    errors.add(new ErrorMsg("001: System error | " + ExceptionUtils.getStackTrace(exp), "Ошибка удалении комментариев!", "CommentError", 0));
                }
                try{
                    personService.delete(request.params(":id"));
                    UploadFile.deleteDirectory(AppConfig.FILE_STORAGE_PATH + "photo/persons/" + person.getAccountId() + "/" + person.getId());
                } catch (Exception exp){
                    errors.add(new ErrorMsg("001: System error | " + ExceptionUtils.getStackTrace(exp), "Ошибка при удалении контакта!",
                            "OfferError", 0));
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
