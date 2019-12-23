package resource;

import auxclass.PhoneBlock;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import configuration.AppConfig;
import entity.Organisation;
import entity.Person;
import entity.Rating;
import entity.User;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.OrganisationService;
import service.PersonService;
import service.RatingService;
import service.UserService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static spark.Spark.get;
import static spark.Spark.post;

public class RatingResource {
    Logger logger = LoggerFactory.getLogger(RatingResource.class);
    Gson gson = new Gson();

    private final RatingService ratingService;
    private final PersonService personService;
    private final UserService userService;
    private final OrganisationService orgService;

    public RatingResource(RatingService ratingService, PersonService personService, UserService userService, OrganisationService orgService) {
        this.personService = personService;
        this.userService = userService;
        this.orgService = orgService;
        this.ratingService = ratingService;
        setupEndpoints();

    }

    private void setupEndpoints() {

        get(AppConfig.API_CONTEXT + "/rating/get", "application/json", (request, response) -> {

            Map<String, Object> result = new HashMap<>();

            Long objId = null;
            String type = null;

            User us = request.session().attribute("user");
            Long accountId = us.getAccountId();
            Long userId = us.getId();

            if (request.queryParams("objId") != null) {
                objId = Long.parseLong(request.queryParams("objId"));
            }

            if (request.queryParams("objType") != null) {
                type = request.queryParams("objType");
            };

            if(userId != null && objId != null && type != null){
                Rating rating = ratingService.get(objId, userId, type);
                rating.setPhones(null);
                result.put("response", "ok");
                result.put("result", rating);
            } else {
                result.put("response", "error");
                result.put("result", "user no found");
            }

            return result;
        }, gson::toJson);

        post(AppConfig.API_CONTEXT + "/rating/save", "application/json", (request, response) -> {

            Map<String, Object> result = new HashMap<>();
            User us = request.session().attribute("user");
            Long accountId = us.getAccountId();
            Long userId = us.getId();
            Rating rating = gson.fromJson(request.body(), Rating.class);
            rating.setAgentId(userId);
            //Если не передан id или тип объекта то ругаемся
            if (rating.getObjId() != null || rating.getObjType() != null) {
                List<String> phones = null;

                //В зависимости от типа объекта получаем список номеров
                if(rating.getObjType().equals("person")){
                    Person person = personService.get(rating.getObjId());
                    if(person != null) {
                        phones = person.getPhoneBlock().getAsList();
                    }
                } else if(rating.getObjType().equals("user")){
                    User user = userService.get(rating.getObjId());
                    if(user != null) {
                        phones = user.getPhoneBlock().getAsList();
                    }
                } else if(rating.getObjType().equals("organisation")){
                    Organisation org = orgService.get(rating.getObjId());
                    if(org != null) {
                        phones = org.getPhoneBlock().getAsList();
                    }
                }
                //Если список номеров пустой отругиваемся
                if(phones.size() != 0){
                    //сохраняем и обновляем индексы
                    rating.setPhones(phones.toArray(new String[phones.size()]));
                    rating = ratingService.save(rating);
                    ratingService.refresh();
                    //В зависимости от типа объекта пересчитываем у объектов средний рейтинг
                    if(!rating.getObjType().equals("organisation")){
                        personService.updateRating(userId,phones);
                        userService.updateRating(userId,phones);
                    } else{
                        orgService.updateRating(userId,phones);
                    }

                    //В зависимости от типа объекта свежий рейтинг
                    if(rating.getObjType().equals("person")){
                        Person person = personService.get(rating.getObjId());
                        if(person != null) {
                            rating.setAvarege_mark(person.getRate());
                        }
                    } else if(rating.getObjType().equals("user")){
                        User user = userService.get(rating.getObjId());
                        if(user != null) {
                            rating.setAvarege_mark(user.getRate());
                        }
                    } else if(rating.getObjType().equals("organisation")){
                        Organisation org = orgService.get(rating.getObjId());
                        if(org != null) {
                            rating.setAvarege_mark(org.getRate());
                        }
                    }
                    result.put("response", "ok");
                    result.put("result", rating);
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

    }

}
