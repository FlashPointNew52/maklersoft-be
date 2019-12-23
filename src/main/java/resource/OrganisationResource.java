package resource;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static spark.Spark.get;
import static spark.Spark.post;

import auxclass.ErrorMsg;
import auxclass.PhoneBlock;
import auxclass.UploadFile;
import com.google.gson.Gson;
import entity.Comment;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import configuration.AppConfig;
import service.*;
import entity.Organisation;
import entity.User;
import utils.CommonUtils;

public class OrganisationResource {

    Logger logger = LoggerFactory.getLogger(OrganisationResource.class);
    Gson gson = new Gson();

    private final OrganisationService orgService;
    private final OfferService offerService;
    private final RequestService requestService;
    private final PersonService personService;
    private final UserService userService;
    private final CommentService commentService;
    private final RatingService ratingService;


    public OrganisationResource(OrganisationService orgService,
                                OfferService offerService,
                                RequestService requestService,
                                PersonService personService,
                                UserService userService,
                                CommentService commentService,
                                RatingService ratingService) {
        this.orgService = orgService;
        this.offerService = offerService;
        this.requestService = requestService;
        this.personService = personService;
        this.userService = userService;
        this.commentService = commentService;
        this.ratingService = ratingService;
        setupEndpoints();

    }

    private void setupEndpoints() {

        get(AppConfig.API_CONTEXT + "/organisation/list", "application/json", (request, response) -> {

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
            if(!source.equals("local")){
                filters.put("typeCode", "accounts");
                accountId = null;
            }

            List<Organisation> orgList = orgService.list(accountId, userId, page, perPage, filters, sort, searchQuery);
            if(!source.equals("local")){
                for (Organisation org: orgList) {
                    Organisation ref = orgService.getByPhone(us.getAccountId(), org.getPhoneBlock().getAsList(), null);
                    if(ref != null)
                        org.setOrgRef(ref.getId());
                    else
                        org.setOrgRef(null);
                }
            }

            result.put("response", "ok");
            result.put("result", orgList);

            return result;
        }, gson::toJson);

        get(AppConfig.API_CONTEXT + "/organisation/get/:id", "application/json", (request, response) -> {

            Map<String, Object> result = new HashMap<>();

            String orgIdStr = request.params(":id");
            if (orgIdStr != null && StringUtils.isNumeric(orgIdStr)) {

                long id = Long.parseLong(orgIdStr);
                Organisation org = orgService.get(id);

                result.put("response", "ok");
                result.put("result", org);
            } else {
                result.put("response", "fail");
                result.put("result", "id is not numeric");
            }

            return result;
        }, gson::toJson);


        post(AppConfig.API_CONTEXT + "/organisation/save", "application/json", (request, response) -> {

            Map<String, Object> result = new HashMap<>();
            User us = request.session().attribute("user");
            Long accountId = us.getAccountId();
            Long userId = us.getId();

            Organisation organisation = gson.fromJson(request.body(), Organisation.class);
            organisation.setAccountId(accountId);
            List<ErrorMsg> errors = orgService.check(organisation);
            if (errors.size() == 0) {

                Organisation link = orgService.getByPhone(null, organisation.getPhoneBlock().getAsList(), organisation.getId());
                if(link != null)
                    organisation.setOrgRef(link.getId());
                else
                    organisation.setOrgRef(null);
                try{
                    Organisation org = orgService.save(organisation);
                    if(org.getId() != null){
                        try{
                            this.offerService.updateOffers("ctx._source.contactType = \""+ organisation.getTypeCode() +"\";" +
                                            "ctx._source.contactName = \""+ CommonUtils.replaceSymb(organisation.getName()) + "\";" +
                                            "ctx._source.isMiddleman = \""+ (organisation.getIsMiddleman() ? "middleman": "owner") + "\";",
                                    new HashMap<String, Object>() {{ put("accountId", accountId.toString()); put("companyId", organisation.getId().toString());}}
                            );
                            this.offerService.updateOffers("ctx._source.orgName = \""+ CommonUtils.replaceSymb(organisation.getName()) + "\";",
                                    new HashMap<String, Object>() {{ put("accountId", accountId.toString()); put("orgId", organisation.getId().toString());}}
                            );
                        }catch (Exception exp){
                            errors.add(new ErrorMsg("900: System error | " + ExceptionUtils.getStackTrace(exp), "Ошибка при обновлении списка предложений", "OfferError", 0));
                        }

                        try{
                            this.requestService.updateRequest("ctx._source.contactType = \""+ organisation.getTypeCode() +"\";" +
                                            "ctx._source.contactName = \""+ CommonUtils.replaceSymb(organisation.getName()) + "\";" +
                                            "ctx._source.isMiddleman = \""+ (organisation.getIsMiddleman() ? "middleman": "owner") + "\";",
                                    new HashMap<String, Object>() {{ put("accountId", accountId.toString()); put("companyId", organisation.getId().toString());}}
                            );
                            this.requestService.updateRequest("ctx._source.orgName = \""+ CommonUtils.replaceSymb(organisation.getName()) + "\";",
                                    new HashMap<String, Object>() {{ put("accountId", accountId.toString()); put("orgId", organisation.getId().toString());}}
                            );
                            result.put("response", "ok");
                            result.put("result", org);
                            response.status(201);
                        }catch (Exception exp){
                            errors.add(new ErrorMsg("900: System error | " + ExceptionUtils.getStackTrace(exp), "Ошибка при обновлении списка заявок", "RequestError", 0));
                        }
                    }
                } catch(Exception exp){
                    errors.add(new ErrorMsg("900: System error | " + ExceptionUtils.getStackTrace(exp), "Ошибка при сохранении организации", "ЩкпфтшыфешщтError", 0));
                }
            }
            if(errors.size() != 0){
                response.status(400);
                return  errors.get(0);
            }
            return result;
        }, gson::toJson);

        post(AppConfig.API_CONTEXT + "/organisation/findByPhone", "application/json", (request, response) -> {
            Map<String, Object> result = new HashMap<>();
            PhoneBlock phones = gson.fromJson(request.body(), PhoneBlock.class);
            User us = request.session().attribute("user");
            Long accountId = us.getAccountId();
            Long userId = us.getId();

            Organisation org = orgService.getByPhone(phones.getAsList(), us.getAccountId());
            result.put("response", "ok");
            result.put("result", org);
            response.status(201);

            return result;

        }, gson::toJson);

        get(AppConfig.API_CONTEXT + "/organisation/delete/:id", "application/json", (request, response) -> {
            Map<String, Object> result = new HashMap<>();
            try{
                Organisation org = orgService.get(Long.parseLong(request.params(":id")));
                if(org.getIsAccount()){
                    ErrorMsg err = new ErrorMsg(" ", "Невозможно удалить аккаунт", "OrgError", 1);
                    response.status(400);
                    return gson.toJson(err);
                }
                List<ErrorMsg> errors = new LinkedList<ErrorMsg>();
                User us = request.session().attribute("user");
                Long accountId = us.getAccountId();
                try{
                    orgService.updateOrganisation("ctx._source.mainOffice = \"\";" + "ctx._source.mainOfficeId = null;",
                            new HashMap<String, Object>() {{ put("accountId", accountId.toString()); put("mainOfficeId", org.getId());}});
                } catch (Exception exp){
                    errors.add(new ErrorMsg("001: System error | " + ExceptionUtils.getStackTrace(exp), "Ошибка при обновлении связанных организаций!", "OrgError", 0));
                }
                try{
                    personService.updatePerson("ctx._source.organisation = \"\";" + "ctx._source.organisationId = null;",
                            new HashMap<String, Object>() {{ put("accountId", accountId.toString()); put("organisationId", org.getId());}});
                } catch (Exception exp){
                    errors.add(new ErrorMsg("001: System error | " + ExceptionUtils.getStackTrace(exp), "Ошибка при обновлении связанных контактов!", "PersonError", 0));
                }
                try{
                    userService.updateUser("ctx._source.organisationId = null;",
                            new HashMap<String, Object>() {{ put("accountId", accountId.toString()); put("organisationId", org.getId());}});
                } catch (Exception exp){
                    errors.add(new ErrorMsg("001: System error | " + ExceptionUtils.getStackTrace(exp), "Ошибка при обновлении связанных пользователей!", "UserError", 0));
                }
                try{
                    long res = offerService.deleteWithFiles(new HashMap<String, Object>(){{ put("accountId", accountId); put("companyId", org.getId());}});
                    logger.info("deleted offer: " + res);
                } catch (Exception exp){
                    errors.add(new ErrorMsg("001: System error | " + ExceptionUtils.getStackTrace(exp), "Ошибка при удалении связанных объектов!", "OfferError", 0));
                }
                try{
                    long res = requestService.deleteWithFiles(new HashMap<String, Object>(){{ put("accountId", accountId); put("companyId", org.getId());}});
                    logger.info("deleted request: " + res);
                } catch (Exception exp){
                    errors.add(new ErrorMsg("001: System error | " + ExceptionUtils.getStackTrace(exp), "Ошибка при удалении связанных объектов!", "RequestError", 0));
                }
                //Пока не удалось проверить работоспособность
                try{
                    this.offerService.updateOffers("ctx._source.orgName = \"\"; ctx._source.orgId = null;",
                            new HashMap<String, Object>() {{ put("accountId", accountId.toString()); put("orgId", org.getId());}}
                    );
                } catch (Exception exp){
                    errors.add(new ErrorMsg("001: System error | " + ExceptionUtils.getStackTrace(exp), "Ошибка при обновлении списка предложений", "OfferError", 0));
                }
                try{
                    this.requestService.updateRequest("ctx._source.orgName = \"\"; ctx._source.orgId = null;",
                            new HashMap<String, Object>() {{ put("accountId", accountId.toString()); put("orgId", org.getId());}}
                    );
                } catch (Exception exp){
                    errors.add(new ErrorMsg("001: System error | " + ExceptionUtils.getStackTrace(exp), "Ошибка при обновлении списка заявок", "RequestError", 0));
                }

                try{
                    long res = ratingService.deleteByQuery(new HashMap<String, Object>(){{put("objType", "organisation"); put("objId", org.getId());}});
                    logger.info("deleted rating: " + res);
                } catch (Exception exp){
                    errors.add(new ErrorMsg("001: System error | " + ExceptionUtils.getStackTrace(exp), "Ошибка при очистке рейтинга!", "RatingError", 0));
                }
                try{
                    long res = commentService.deleteByQuery(new HashMap<String, Object>(){{put("objType", "organisation"); put("objId", org.getId());}});
                    logger.info("deleted comment: " + res);
                } catch (Exception exp){
                    errors.add(new ErrorMsg("001: System error | " + ExceptionUtils.getStackTrace(exp), "Ошибка удалении комментариев!", "CommentError", 0));
                }
                try{
                    orgService.delete(request.params(":id"));
                    UploadFile.deleteDirectory(AppConfig.FILE_STORAGE_PATH + "photo/organisation/" + org.getAccountId() + "/" + org.getId());
                } catch (Exception exp){
                    errors.add(new ErrorMsg("001: System error | " + ExceptionUtils.getStackTrace(exp), "Ошибка при удалении организации!",
                            "OrganisationError", 0));
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
