package resource;
/**
 * Created by owl on 4/5/16.
 */

import java.util.HashMap;
import java.util.Map;

import static spark.Spark.post;

import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import configuration.AppConfig;
import service.PersonService;
import service.UserService;
import service.OfferService;

import entity.Person;
import entity.User;
import entity.Offer;


public class Maintenance {

    Logger logger = LoggerFactory.getLogger(Maintenance.class);
    Gson gson = new Gson();

    private final OfferService offerService;
    private final UserService userService;
    private final PersonService personService;


    public Maintenance(OfferService offerService, UserService userService, PersonService personService) {

        this.offerService = offerService;
        this.userService = userService;
        this.personService = personService;
        setupEndpoints();

    }

    private void setupEndpoints() {

        post(AppConfig.SERVICE_CONTEXT + "/offer/put", "application/json", (request, response) -> {
            Map<String, Object> result = new HashMap<>();

            Offer offer = gson.fromJson(request.body(), Offer.class);
            Offer res = offerService.save(offer);

            result.put("response", "ok");
            result.put("result", res);
            response.status(202);

            return result;
        }, gson::toJson);

        post(AppConfig.SERVICE_CONTEXT + "/person/put", "application/json", (request, response) -> {
            Map<String, Object> result = new HashMap<>();

            Person person = gson.fromJson(request.body(), Person.class);
            Person r = personService.save(person);

            result.put("response", "ok");
            result.put("result", r);
            response.status(202);

            return result;
        }, gson::toJson);

        post(AppConfig.SERVICE_CONTEXT + "/user/put", "application/json", (request, response) -> {
            Map<String, Object> result = new HashMap<>();

            User user = gson.fromJson(request.body(), User.class);
            User r = userService.save(user);

            result.put("response", "ok");
            result.put("result", r);
            response.status(202);

            return result;
        }, gson::toJson);

    }

}
