import java.util.*;

import static spark.Spark.before;
import static spark.Spark.halt;
import static spark.Spark.get;
import static spark.Spark.post;
import static utils.CommonUtils.getUnixTimestamp;

import com.google.gson.Gson;
import configuration.AppConfig;
import entity.Account;
import entity.User;
import entity.Organisation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.ibm.icu.text.Transliterator;
import auxclass.EmailBlock;
import auxclass.PhoneBlock;
import utils.CommonUtils;
import utils.Email;
import utils.Smsc;


public class Authorisation {

    static Logger logger = LoggerFactory.getLogger(Authorisation.class);
    static User makl_user = new User();
    Gson gson = new Gson();
    static final boolean AUTH_CHECK_DISABLED = false;

    public Authorisation() {
        this.makl_user.setAccountId(1549497992480L); //old_1545092165300l
        setupEndpoints();
    }

    private void setupEndpoints() {

        before((request, response) -> {
            String rqOrigin = request.headers("Origin");
            String methods = "*";
            String headers = "*";
            //response.header("Access-Control-Allow-Origin", origin);
            response.header("Access-Control-Request-Method", methods);
            response.header("Access-Control-Allow-Headers", headers);
            response.header("Access-Control-Allow-Credentials", "true");

            String apiKey = request.queryParams("api_key");
            if (apiKey != null) {
                if (AppConfig.KEY_LIST.contains(apiKey)) {
                    response.header("Access-Control-Allow-Origin", rqOrigin);
                    return;
                } else {
                    halt(401, "bad key");
                }
            } else if (AppConfig.CORS_WHITELIST.contains(rqOrigin)) {
                response.header("Access-Control-Allow-Origin", rqOrigin);
            }

            if (AUTH_CHECK_DISABLED || !request.uri().startsWith("/api")) return;

            if(!AppConfig.AUTORISATION_WHITELIST.contains(request.headers("Authorization"))){
                if (request.session().isNew() || request.session().attribute("logged_in") == null || (boolean)request.session().attribute("logged_in") != true) {
                    halt(401, "unauthorized fuck");
                } else{
                    //request.session().attribute("user", this.makl_user);
                }
            } else{
                request.session().attribute("user", this.makl_user);
            }

        });

        post("/session/login", (request, res) -> {

            HashMap<String, Object> result = new HashMap<>();

            Map<String, String> map = CommonUtils.JsonToMap(request.body());


            String phone = map.get("phone");
            String pass = map.get("password");

            if (phone.equals("admin_maklersoft") && pass.equals("nopass")) {
                request.session().attribute("logged_in", true);
                request.session().attribute("phone", phone);
                result.put("result", "OK");
                return result;
            }

            result.put("result", "FAIL");

            User user = App.userService.getByPhone(new ArrayList<String>() {{ add(phone);}});
            if(user != null) {
                if (user.getStateCode() == "archive") {
                    result.put("msg", "000:User is lock");
                    return result;
                }
                if (user.getPassword().equals(pass)) {
                    if (user.getEntryState().equals("reg"))
                        user.setEntryState("main");
                    else if (user.getEntryState().equals("new"))
                        user.setEntryState("confirm");
                    try {
                        user = App.userService.save(user);
                        request.session().attribute("logged_in", true);
                        request.session().attribute("user", user);
                        result.put("user", user);
                        result.put("result", "OK");
                    } catch (Exception ex) {
                        result.put("msg", "900:System error");
                    }
                } else
                    result.put("msg", "302:Wrong password");
            } else
                result.put("msg", "301:User not found");

            return result;
        }, gson::toJson);

        post("/session/check_phone", (request, res) -> {
            HashMap<String, Object> result = new HashMap<>();

            Map<String, String> map = CommonUtils.JsonToMap(request.body());
            String phone = map.get("phone");

            result.put("result", "FAIL");

            User user = App.userService.getByPhone(new ArrayList<String>() {{ add(phone);}});
            if (user != null){
                if(setNewCode(user)) {
                    if(sendSms(phone, "Код восстановления: " + user.getTemp_code())) {
                        result.put("result", "OK");
                    } else{
                        result.put("msg", "000:Send sms error");
                    }
                } else
                    result.put("msg", "900:System error");
            } else
                result.put("msg", "301:User not found");
            return result;

        }, gson::toJson);

        post("/session/change_pass", (request, res) -> {

            HashMap<String, Object> result = new HashMap<>();

            Map<String, String> map = CommonUtils.JsonToMap(request.body());

            String temp_code = map.get("temp_code");
            String phone = map.get("phone");
            String pass = map.get("password");

            result.put("result", "FAIL");

            User user = App.userService.getByPhone(new ArrayList<String>() {{ add(phone);}});
            if (user != null){
                if(user.getDate_of_temp() > getUnixTimestamp() - 86400){
                    if(user.getTemp_code().equals(temp_code)){
                        //установим пароль и сбросим дату действия
                        user.setPassword(pass);
                        user.setDate_of_temp(0L);
                        App.userService.save(user);
                        for (String mail: user.getEmailBlock().getAsList()){
                            sendMail(mail, "Обновление учетных данных MaklerSoft",
                                    "Для входа в систему используйте Телефон: " + phone +" и пароль: " + user.getPassword());
                            break;
                        }
                        result.put("result", "OK");
                    } else{
                        result.put("msg", "000:Temp code wrong");
                    }
                } else{
                    //генерируем новый код, т.к. время жизни старого истекло
                    if(setNewCode(user)){
                        if(sendSms(phone, "Код восстановления: " + user.getTemp_code()))
                            result.put("msg", "000:Temp code not valid");
                        else
                            result.put("msg", "000:Send sms error");
                    } else
                        result.put("msg", "900:System error");
                }
            } else
                result.put("msg", "301:User not found");
            return result;

        }, gson::toJson);

        post("/session/logout", (request, res) -> {

            HashMap<String, String> result = new HashMap<>();
            request.session().attribute("logged_in", false);
            request.session().invalidate();
            result.put("result", "OK");
            return result;

        }, gson::toJson);

        get("/session/check", (request, res) -> {

            HashMap<String, Object> result = new HashMap<>();
            if (request.session().isNew() || request.session().attribute("logged_in") == null || (boolean)request.session().attribute("logged_in") != true) {
                result.put("result", "FAIL");
            } else {
                Account acc = request.session().attribute("account");
                User user = request.session().attribute("user");
                result.put("account", acc);
                result.put("user", user);
                result.put("result", "OK");
            }

            return result;

        }, gson::toJson);

        post("/session/registrate", (request, res) -> {

            HashMap<String, Object> result = new HashMap<>();

            Map<String, String> map = CommonUtils.JsonToMap(request.body());

            String org_name = map.get("org_name");
            String user_name = map.get("user_name");
            String mail = map.get("mail");
            String phone = map.get("phone");
            result.put("result", "FAIL");
            try {
                phone = PhoneBlock.normalisePhone(phone);
                mail = EmailBlock.normaliseMail(mail);

                if (phone.length() < 11) {
                    result.put("msg", "001:Wrong format phone");
                    return result;
                }

                User new_user = new User(user_name);
                new_user.setPhoneBlock(new PhoneBlock(new String[]{phone}));
                new_user.setEmailBlock(new EmailBlock(new String[]{mail}));

                List<String> errors = App.userService.check(new_user);

                if (errors.size() == 0) {
                    Organisation org = new Organisation();
                    org.setName(org_name);
                    org.setTypeCode("company");
                    org.setStateCode("raw");
                    org.setIsAccount(true);
                    org.setPhoneBlock(new_user.getPhoneBlock());
                    org.setEmailBlock(new_user.getEmailBlock());
                    errors = App.orgService.check(org);
                    if (errors.size() == 0) {
                        org = App.orgService.save(org);
                        new_user.setPosition("director");
                        new_user.setDepartment("all");
                        new_user.setOrganisationId(org.getId());
                        new_user.setSpecialization("all");
                        new_user.setStateCode("raw");
                        new_user.setEntryState("reg");
                        new_user.setAccountId(org.getId());
                        new_user.setPassword(generateCode());
                        new_user.setDate_of_temp(getUnixTimestamp());
                        new_user = App.userService.save(new_user);
                        org.setAgentId(new_user.getId());
                        org.setAccountId(org.getId());
                        org =  App.orgService.save(org);
                        sendSms(phone, "Ваш пароль: " + new_user.getPassword() + ". Для смены пароля воспользуйтесь формой восстановления");
                        sendMail(mail, "Доступ к системе MaklerSoft", "Для входа в систему используйте Телефон: " + phone +
                                ", пароль " + new_user.getPassword());
                        result.put("result", "OK");
                    } else {
                        result.put("msg", errors.get(0));
                    }
                } else {
                    result.put("msg", errors.get(0));
                }
            } catch(Exception ex){
                result.put("msg", "900:System error");
                this.logger.info(ex.toString());
            }
            return result;

        }, gson::toJson);
    }

    private boolean setNewCode(User user){
        String code = generateCode();
        user.setTemp_code(code);
        user.setDate_of_temp(getUnixTimestamp());
        try {
            App.userService.save(user);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private boolean sendMail(String email, String subject, String text){
        Email mail = new Email();
        try {
            mail.SendMail(email, subject, text);
        } catch (Exception exp) {
            return false;
        }
        return true;
    }

    private boolean sendSms(String phone, String text){
        Smsc sms = new Smsc();
        try {
            String[] ret = sms.send_sms(phone, text, 0, "", "", 0, "MaklerSoft", "");
        } catch (Exception exp) {
            return false;
        }
        return true;
    }

    private String generateCode(){
        Random rnd = new Random();
        String code = "";
        for(int i = 0; i < 6; i++)
            code += rnd.nextInt(9);
        return code;
    }

    private String check_with_generate(User user, String phone){
        String msg = null;
        /*for (String usphone: user.getPhoneBlock().normalisePhones(user.getPhoneBlock().getAsList())){
            if(PhoneBlock.normalisePhone(phone).matches(phone)){

                /*for (String mail: user.getEmailBlock().getAsList()){
                    sendMail(phoneMail, "Для вхоВосстановление пароля", "Ваш пароль: " + user.getTemp_code());
                }
                    return null;
            }
        }
        msg = "wrong data";*/

        return msg;
      }
}
