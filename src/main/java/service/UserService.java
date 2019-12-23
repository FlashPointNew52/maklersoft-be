package service;

import auxclass.ErrorMsg;
import auxclass.PhoneBlock;
import com.google.gson.Gson;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.UpdateByQueryAction;
import org.elasticsearch.index.reindex.UpdateByQueryRequestBuilder;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;

import utils.*;
import entity.User;
import static utils.CommonUtils.getUnixTimestamp;

public class UserService {

    private final String E_INDEX = "ms_main";
    private final String E_TYPE = "user";
    private final String E_DATAFIELD = "data";

    Logger logger = LoggerFactory.getLogger(UserService.class);
    private final Client elasticClient;
    private final RatingService ratingService;
    Gson gson = new Gson();

    public UserService (Client elasticClient,
                        RatingService ratingService) {
        this.ratingService = ratingService;
        this.elasticClient = elasticClient;
    }

    public List<String> check1 (User user) {
        // check login, pass, role
        List<String> errors = new LinkedList<>();

        if (user.getPassword() == null || user.getPassword().length() < 4) {
            //errors.add("password is null or too short");
            user.setPassword("");
        }

        return errors;
    }

    public List<ErrorMsg> check (User user) {
        List<ErrorMsg> errors = new LinkedList<>();
        try{
            /*if(user.getAccountId() == null){
                errors.add("001:account not specified");
            }*/
            if((user.getEmailBlock() == null && user.getPhoneBlock() == null) || user.getEmailBlock().getAsList().size() + user.getPhoneBlock().getAsList().size() == 0 ){
                errors.add(new ErrorMsg("200: No phones or emails|", "Не указан номер телефона или емайл", "UserError", 1));
            } else{
                SearchRequestBuilder rb = elasticClient.prepareSearch(E_INDEX)
                        .setTypes(E_TYPE)
                        .setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
                        .setFrom(0).setSize(1);

                BoolQueryBuilder q = QueryBuilders.boolQuery();
                BoolQueryBuilder qw = QueryBuilders.boolQuery().minimumShouldMatch(1);
                for (String mail: user.getEmailBlock().getAsList()) {
                    qw.should(QueryBuilders.matchQuery("emails", mail.replaceAll("@", "")));
                }
                for (String phone: user.getPhoneBlock().getAsList()) {
                    qw.should(QueryBuilders.matchQuery("phones", phone));
                }
                q.must(qw);
                if(user.getId() != null){
                    q.mustNot(QueryBuilders.termQuery("id", user.getId()));
                }
                rb.setQuery(q);

                SearchResponse response = rb.execute().actionGet();

                for (SearchHit sh: response.getHits()) {
                    String dataJson = sh.getSourceAsMap().get(E_DATAFIELD).toString();
                    errors.add(new ErrorMsg("300: User with such email or phone already exists" + dataJson,
                            "Пользователь с таким телефоном или емайл уже существует", "UserError",1));
                }
            }
        } catch(Exception exp){
            errors.add(new ErrorMsg("900: System error | " + ExceptionUtils.getStackTrace(exp),"Системная ошибка", "UserError",0));
        }
        return errors;

    }

    public List<User> list (Long accountId, Long userId, int page, int perPage,  Map<String, Object> filter, Map<String, String> sort, String searchQuery) {

        List<User> userList = new ArrayList<>();

        SearchRequestBuilder rb = elasticClient.prepareSearch(E_INDEX)
                .setTypes(E_TYPE)
                .setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
                .setFrom(page * perPage).setSize(perPage);

        BoolQueryBuilder q = QueryBuilders.boolQuery();

        ParseResult pr = Query.parseContacts(searchQuery);
        List<FilterObject> rangeFilters = pr.filterList;

        if (pr.query != null && pr.query.length() > 2) {
            q.should(QueryBuilders.matchQuery("name", pr.query));
            q.should(QueryBuilders.matchQuery("emails", pr.query));
        }

        filter.forEach((k,v) -> {
            if (v != null && !v.equals("all")) {
                if (k.equals("changeDate") || k.equals("addDate")) {
                    long date = Long.parseLong(v.toString());
                    // 86400 sec in 1 day
                    long ts = CommonUtils.getUnixTimestamp() - date * 86400;
                    q.must(QueryBuilders.rangeQuery(k).gte(ts));
                } else if (k.equals("cashed")) {
                    q.must(QueryBuilders.termQuery("agentId", userId));
                } else if (k.equals("typeCode") && v.equals("company")) {
                    q.must(QueryBuilders.termQuery("accountId", accountId));
                } else
                    q.must(QueryBuilders.termQuery(k, v));
            }
        });

        rangeFilters.forEach(fltr -> {
            if(fltr.arrayVal != null && fltr.arrayVal.size() > 0){
                BoolQueryBuilder qw = QueryBuilders.boolQuery().minimumShouldMatch(1);
                for (String val: fltr.arrayVal) {
                    qw.should(QueryBuilders.termQuery(fltr.fieldName, val));
                }
                q.must(qw);
            }
        });

        if (sort == null || sort.size() == 0) {
            rb.addSort(SortBuilders.fieldSort("changeDate").order(SortOrder.DESC));
        } else {
            sort.forEach((k, v) -> {
                if (v.equals("ASC")) {
                    rb.addSort(SortBuilders.fieldSort(k).order(SortOrder.ASC));
                } else if (v.equals("DESC")) {
                    rb.addSort(SortBuilders.fieldSort(k).order(SortOrder.DESC));
                }
            });
        }
        rb.setQuery(q);
        SearchResponse response = rb.execute().actionGet();

        for (SearchHit sh: response.getHits()) {
            String dataJson = sh.getSourceAsMap().get(E_DATAFIELD).toString();
            User usr = gson.fromJson(dataJson, User.class);
            usr.setOrganisationId(sh.getSourceAsMap().get("organisationId") != null ? Long.parseLong(sh.getSourceAsMap().get("organisationId").toString()): null);
            if(usr.getOrganisationId() == null) usr.setOrganisation(null);
            usr.setAgentId(sh.getSourceAsMap().get("agentId") != null ? Long.parseLong(sh.getSourceAsMap().get("agentId").toString()): null);
            if(usr.getAgentId() == null) usr.setAgent(null);
            userList.add(usr);
        }

        return userList;
    }

    //пересчет рейтинга у пользователей
    public void updateRating (Long userId, List<String> phones) {
        SearchRequestBuilder rb = elasticClient.prepareSearch(E_INDEX)
                .setTypes(E_TYPE)
                .setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
                .setFrom(0).setSize(10000);

        BoolQueryBuilder q = QueryBuilders.boolQuery();
        BoolQueryBuilder bq = QueryBuilders.boolQuery();
        //ищем все контаккты по тем номерам по которым оставлен  рейтинг
        for(String phone: phones) {
            bq.should(QueryBuilders.matchQuery("phones", phone));
        }
        q.must(bq);

        rb.addSort(SortBuilders.fieldSort("changeDate").order(SortOrder.DESC));
        rb.setQuery(q);

        SearchResponse response = rb.execute().actionGet();
        //Для каждого пользователя высчитываем рейтинг и обновляем контакт
        for (SearchHit sh: response.getHits()) {
            String dataJson = sh.getSourceAsMap().get(E_DATAFIELD).toString();
            User usr = gson.fromJson(dataJson, User.class);
            usr.setRate(ratingService.getAverage(usr.getPhoneBlock().getAsList(), "user"));
            try {
                usr = save(usr);
            } catch(Exception exp){}
        }
    }

    public User get (long id) {
        User result = null;

        try {
            GetResponse response = this.elasticClient.prepareGet(E_INDEX, E_TYPE, Long.toString(id)).get();
            String dataJson = response.getSourceAsMap().get(E_DATAFIELD).toString();
            result = gson.fromJson(dataJson, User.class);
        } catch (Exception exp){}

        return result;
    }

    public User getByLogin (long accountId, String login) {

        User result = null;


        SearchRequestBuilder rb = elasticClient.prepareSearch(E_INDEX)
                .setTypes(E_TYPE)
                .setSearchType(SearchType.DFS_QUERY_THEN_FETCH);


        BoolQueryBuilder q = QueryBuilders.boolQuery();

        q.must(QueryBuilders.matchQuery("login", login));
        q.must(QueryBuilders.matchQuery("accountId", accountId));

        rb.setQuery(q);

        SearchResponse response = rb.execute().actionGet();

        if (response.getHits().getTotalHits() > 0) {
            String dataJson = response.getHits().getAt(0).getSourceAsMap().get(E_DATAFIELD).toString();
            result = gson.fromJson(dataJson, User.class);
        }

        return result;
    }

    public User getByPhone (List<String> phones) {

        SearchRequestBuilder rb = elasticClient.prepareSearch(E_INDEX)
                .setTypes(E_TYPE)
                .setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
                .setFrom(0 * 1).setSize(1);


        phones = PhoneBlock.normalisePhones(phones);
        BoolQueryBuilder q = QueryBuilders.boolQuery();
        BoolQueryBuilder qw = QueryBuilders.boolQuery().minimumShouldMatch(1);

        for (String phone: phones) {
            qw.should(QueryBuilders.matchQuery("phones", phone));
        }
        q.must(qw);
        rb.setQuery(q);
        SearchResponse response = rb.execute().actionGet();

        User user = null;
        for (SearchHit sh: response.getHits()) {
            String dataJson = sh.getSourceAsMap().get(E_DATAFIELD).toString();
            user = gson.fromJson(dataJson, User.class);
            break;
        }

        return user;
    }

    public User save (User user) throws Exception {
        user.setRate(ratingService.getAverage(user.getPhoneBlock().getAsList(), "user"));
        user.preIndex();
        indexUser(user);

        return user;
    }

    public RestStatus delete (String id) {
        DeleteResponse response = this.elasticClient.prepareDelete(this.E_INDEX, this.E_TYPE, id).get();
        return response.status();
    }


    public void indexUser(User user) {

        Map<String, Object> json = new HashMap<>();

        json.put("id", user.getId());
        json.put("accountId", user.getAccountId());
        json.put("name", CommonUtils.strNotNull(user.getName()));
        json.put("changeDate", user.getChangeDate());
        json.put("addDate", user.getAddDate());
        json.put("assignDate", user.getAssignDate());

        List<String> phoneArray = user.getPhoneBlock().getAsList();
        json.put("phones", String.join(" ", phoneArray));

        List<String> mailArray = user.getEmailBlock().getAsList();
        json.put("emails", String.join(" ", mailArray).replace("@",""));

        json.put("tag", user.getTag());

        /*if(user.getPosition() == null)
            user.setPosition("realtor");*/
        if(user.getStateCode() == null)
            user.setStateCode("raw");
        if(user.getEntryState() == null)
            user.setEntryState("new");
        /*if(user.getDepartment() == null)
            user.setDepartment("all");
        if(user.getSpecialization() == null)
            user.setSpecialization("all");
        if(user.getRate() == null)
            user.setRate(0);*/
        json.put("position", user.getPosition());
        json.put("stateCode", user.getStateCode());
        json.put("department", user.getDepartment());
        json.put("specialization", user.getSpecialization());
        json.put("rate", user.getRate());
        // filters
        json.put("agentId", user.getAgentId());
        json.put("organisationId", user.getOrganisationId());

        json.put(E_DATAFIELD, gson.toJson(user));


        IndexResponse response = this.elasticClient.prepareIndex(E_INDEX, E_TYPE, Long.toString(user.getId())).setSource(json).get();
    }

    public void updateUser(String script_text, Map<String, Object> filter) {
        UpdateByQueryRequestBuilder ubqrb = UpdateByQueryAction.INSTANCE.newRequestBuilder(this.elasticClient);
        /*script_text += "ctx._source.tags = ctx._source.name + ' ' + ctx._source.agent + ' ' + " +
                "ctx._source.organisation;";*/

        Script script = new Script(script_text);
        BoolQueryBuilder q = QueryBuilders.boolQuery();
        filter.forEach((k, v) -> {
            q.must(QueryBuilders.termQuery(k, v));
        });
        ubqrb.source(E_INDEX).filter(q).script(script).source().setTypes(E_TYPE);

        BulkByScrollResponse response = ubqrb.execute().actionGet();
    }
}
