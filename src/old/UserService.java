package service;

import auxclass.PhoneBlock;
import com.google.gson.Gson;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilders;
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

    public List<String> check (User user) {
        List<String> errors = new LinkedList<>();
        try{
            /*if(user.getAccountId() == null){
                errors.add("001:account not specified");
            }*/
            if((user.getEmailBlock() == null && user.getPhoneBlock() == null) || user.getEmailBlock().getAsList().size() + user.getPhoneBlock().getAsList().size() == 0 ){
                errors.add("200:No phones or emails");
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

                if (response.getHits().getTotalHits() > 0) {
                    errors.add("300:User with such email or phone already exists");
                }
                for (SearchHit sh: response.getHits()) {
                    String dataJson = sh.getSourceAsMap().get(E_DATAFIELD).toString();
                }
            }
        } catch(Exception ex){
            errors.add("900:System error");
        }
        return errors;

    }

    public List<User> list (Long accountId, Long userId, int page, int perPage,  Map<String, String> filter, Map<String, String> sort, String searchQuery) {

        List<User> userList = new ArrayList<>();

        SearchRequestBuilder rb = elasticClient.prepareSearch(E_INDEX)
                .setTypes(E_TYPE)
                .setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
                .setFrom(page * perPage).setSize(perPage);

        BoolQueryBuilder q = QueryBuilders.boolQuery();

        ParseResult pr = Query.parseContacts(searchQuery.toLowerCase());
        List<FilterObject> rangeFilters = pr.filterList;

        filter.forEach((k,v) -> {
            if (v != null && !v.equals("all")) {
                if (k.equals("changeDate") || k.equals("addDate")) {
                    long date = Long.parseLong(v);
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

        if (pr.query != null && pr.query.length() > 2) {
            q.must(QueryBuilders.matchQuery("tags", pr.query).operator(Operator.AND));
            q.should(QueryBuilders.matchQuery("name", pr.query).boost(8));
            q.should(QueryBuilders.matchQuery("organisation", pr.query).boost(4));
        }

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
            userList.add(gson.fromJson(dataJson, User.class));
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
        indexUser(user);

        return user;
    }

    public User delete (long id) {
        return null;
    }


    public void indexUser(User user) {

        Map<String, Object> json = new HashMap<>();

        if(user.getId() == null) {
            user.setId(CommonUtils.getSystemTimestamp());
            user.setAddDate(getUnixTimestamp());
        }
        user.setChangeDate(getUnixTimestamp());
        json.put("id", user.getId());
        json.put("accountId", user.getAccountId());
        String tags = CommonUtils.strNotNull(user.getName()).toLowerCase();
        json.put("name", tags);
        json.put("changeDate", user.getChangeDate());
        json.put("addDate", user.getAddDate());
        json.put("assignDate", user.getAssignDate());

        List<String> phoneArray = user.getPhoneBlock().getAsList();
        json.put("phones", String.join(" ", phoneArray));

        List<String> mailArray = user.getEmailBlock().getAsList();
        json.put("emails", String.join(" ", mailArray).replace("@",""));

        json.put("tag", user.getTag());

        if(user.getPosition() == null)
            user.setPosition("realtor");
        if(user.getStateCode() == null)
            user.setStateCode("raw");
        if(user.getEntryState() == null)
            user.setEntryState("new");
        if(user.getDepartment() == null)
            user.setDepartment("all");
        if(user.getSpecialization() == null)
            user.setSpecialization("all");
        /*if(user.getRate() == null)
            user.setRate(0);*/
        json.put("position", user.getPosition());
        json.put("stateCode", user.getStateCode());
        json.put("department", user.getDepartment());
        json.put("specialization", user.getSpecialization());
        json.put("rate", user.getRate());
        // filters
        json.put("agentId", user.getAgentId());
        String agent_name = "";
        if(user.getAgent() != null){
            agent_name = CommonUtils.strNotNull(user.getAgent().getName()).toLowerCase().replaceAll("\"", "");
            json.put("agent", agent_name);
        }

        json.put("organisationId", user.getOrganisationId());
        String org_name = "";
        if(user.getOrganisation() != null){
            org_name = CommonUtils.strNotNull(user.getOrganisation().getName()).toLowerCase().replaceAll("\"", "");
            json.put("organisation", org_name);
        }

        tags += " " + agent_name + " " + org_name + " ";
        json.put("tags", tags);

        json.put(E_DATAFIELD, gson.toJson(user));


        IndexResponse response = this.elasticClient.prepareIndex(E_INDEX, E_TYPE, Long.toString(user.getId())).setSource(json).get();
    }
}
