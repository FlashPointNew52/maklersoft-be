package resource;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static spark.Spark.post;

import auxclass.ErrorMsg;
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
import utils.CommonUtils;
import utils.Email;

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

        post(AppConfig.SERVICE_CONTEXT + "/report/err", "application/json", (request, response) -> {
            Map<String, Object> result = new HashMap<>();

            ErrorMsg msg = gson.fromJson(request.body(), ErrorMsg.class);
            User us = request.session().attribute("user");
            if(us == null){
                msg.setUser(new User("Неавторизованный пользователь"));
                msg.getUser().setId(0L);
                msg.getUser().setAccountId(0L);
            } else{
                msg.setUser(us);
            }
            Date date = new Date();
            Email mail = new Email();
            String html=
                "<html>" +
                    "<head>" +
                        "<style>" +
                            "table{" +
                                    "border: 1px solid black;" +
                                    "background-color: lightyellow;" +
                                    "border-collapse: collapse;" +
                                    "font-size: 14px;" +
                            "}" +
                            "td{" +
                                    "border: 1px solid black;" +
                                    "padding: 5px 10px 5px 10px;" +
                            "}" +
                        "</style>" +
                    "</head>" +
                    "<body>" +
                        "<br>" +
                        "<div>Добрый день!</div>" +
                        "<div>В ваш адрес направлен следующий запрос:</div>" +
                        "<br>" +
                        "<table>" +
                        "    <tr>" +
                        "        <td colspan=\"2\" style=\"font-size: 16px; text-align: center;\">Описание системной ошибки " + CommonUtils.getUnixTimestamp() + "</td>" +
                        "    </tr>" +
                        "    <tr>" +
                        "        <td>Дата/Время</td>" +
                        "        <td>" + new SimpleDateFormat("dd MMM YYYY в HH:mm:ss", new Locale("ru")).format(date) + "</td>" +
                        "    </tr>" +
                        "    <tr>" +
                        "        <td>Пользователь</td>" +
                        "        <td>" + msg.getUser().getName() + " (accountId: " + msg.getUser().getAccountId() + ", id: "+ msg.getUser().getId() +")</td>" +
                        "    </tr>" +
                        "    <tr>" +
                        "        <td>Статус</td>" +
                        "        <td>" + msg.getStatus() + " (" + msg.getStatusText() +")</td>" +
                        "    </tr>" +
                        "    <tr>" +
                        "        <td>Ошибка</td>" +
                        "        <td><a href='" + msg.getUrl() + "'>" + msg.getMessage() + "</a></td>" +
                        "    </tr>" +
                        "    <tr>" +
                        "        <td>Стек вызова</td>" +
                        "        <td style=\"font-size: 11px; white-space: pre;\">" + msg.getError() + "</td>" +
                        "    </tr>" +
                        "</table>" +
                        "<br>" +
                        "<div>Сообщение отправлено с сайта <a href='Maklersoft.com'>Maklersoft.com</a></div>" +
                    "</body>" +
                "</html>";
            mail.SendHtmlMail("john.modenov@gmail.com, maklersoft@yandex.ru", "","Maklersoft.com - Ошибка " + msg.getStatus() + " (" +
                    CommonUtils.getUnixTimestamp() +")", html);

            result.put("response", "ok");
            result.put("result", "ok");
            response.status(202);

            return result;
        }, gson::toJson);

    }

}
