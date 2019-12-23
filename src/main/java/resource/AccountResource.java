package resource;

import static spark.Spark.get;
import static spark.Spark.post;
import static spark.Spark.put;

import com.google.gson.Gson;
import configuration.AppConfig;
import entity.Account;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.AccountService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AccountResource {

    Logger logger = LoggerFactory.getLogger(OfferResource.class);
    Gson gson = new Gson();

    private final AccountService accountService;


    public AccountResource(AccountService accountService) {
        this.accountService = accountService;
        setupEndpoints();
    }

    private void setupEndpoints() {
        get(AppConfig.API_CONTEXT + "/account/list", "application/json", (request, response) -> {

            Map<String, Object> result = new HashMap<>();
            List<Account> accountList = accountService.list(0, 100,"");

            result.put("response", "ok");
            result.put("result", accountList);

            return result;
        }, gson::toJson);

        get(AppConfig.API_CONTEXT + "/account/get/:id", "application/json", (request, response) -> {

            Map<String, Object> result = new HashMap<>();
            long id = Long.parseLong(request.params(":id"));
            Account account = accountService.get(id);

            if (account != null) {
                result.put("response", "ok");
                result.put("result", account);
            } else {
                result.put("response", "not found");
            }

            return result;
        }, gson::toJson);

        post(AppConfig.API_CONTEXT + "/account/save", "application/json", (request, response) -> {
            Map<String, Object> result = new HashMap<>();

            Account account = gson.fromJson(request.body(), Account.class);
            Account res = accountService.save(account);

            result.put("response", "ok");
            result.put("result", res);
            response.status(202);

            return result;
        }, gson::toJson);


        post(AppConfig.API_CONTEXT + "/account/delete/:id", "application/json", (request, response) -> {

            Map<String, Object> result = new HashMap<>();
            int id = Integer.parseInt(request.params(":id"));
            Account account = accountService.delete(id);

            result.put("response", "ok");
            result.put("result", account);

            return result;
        }, gson::toJson);
    }
}

