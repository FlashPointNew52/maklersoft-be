package service;

import auxclass.PhoneBlock;
import com.google.gson.Gson;
import entity.Account;
import entity.User;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.index.query.*;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import auxclass.PhoneBlock;
import java.util.*;

import utils.*;
import entity.Organisation;
import static utils.CommonUtils.getUnixTimestamp;

public class OrganisationService {

    private final String E_INDEX = "ms_main";
    private final String E_TYPE = "organisation";
    private final String E_DATAFIELD = "data";

    Logger logger = LoggerFactory.getLogger(OrganisationService.class);
    private final Client elasticClient;
    private final RatingService ratingService;
    Gson gson = new Gson();

    public OrganisationService (Client elasticClient,
                                RatingService ratingService) {
        this.ratingService = ratingService;
        this.elasticClient = elasticClient;
    }

    public List<String> check (Organisation org) {
        List<String> errors = new LinkedList<>();
        try{
            /*if(user.getAccountId() == null){
                errors.add("001:account not specified");
            }*/
            if((org.getEmailBlock() == null && org.getPhoneBlock() == null) || org.getEmailBlock().getAsList().size() + org.getPhoneBlock().getAsList().size() == 0 ){
                errors.add("200:No phones or emails");
            } else{
                SearchRequestBuilder rb = elasticClient.prepareSearch(E_INDEX)
                        .setTypes(E_TYPE)
                        .setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
                        .setFrom(0).setSize(1);

                BoolQueryBuilder q = QueryBuilders.boolQuery();
                BoolQueryBuilder qw = QueryBuilders.boolQuery().minimumShouldMatch(1);
                for (String mail: org.getEmailBlock().getAsList()) {
                    qw.should(QueryBuilders.matchQuery("emails", mail.replaceAll("@", "")));
                }
                for (String phone: org.getPhoneBlock().getAsList()) {
                    qw.should(QueryBuilders.matchQuery("phones", phone));
                }
                q.must(qw);
                if(org.getId() != null){
                    q.mustNot(QueryBuilders.termQuery("id", org.getId()));
                }
                if(org.getAccountId() != null){
                    q.must(QueryBuilders.termQuery("accountId", org.getAccountId()));
                    if(org.getId() != null)
                        q.mustNot(QueryBuilders.termQuery("id", org.getId()));
                } else
                    q.must(QueryBuilders.termQuery("typeCode", "company"));
                rb.setQuery(q);
                SearchResponse response = rb.execute().actionGet();

                if (response.getHits().getTotalHits() > 0) {
                    errors.add("400:Organisation with such email or phone already exists");
                }
                for (SearchHit sh: response.getHits()) {
                    String dataJson = sh.getSourceAsMap().get(E_DATAFIELD).toString();
                }
            }
        } catch(Exception exp){
            errors.add("900:System error");
            this.logger.info("error Org: " + exp.toString());
        }
        return errors;
    }

    public Organisation getByPhone (List<String> phones, Long accountId) {

        SearchRequestBuilder rb = elasticClient.prepareSearch(E_INDEX)
                .setTypes(E_TYPE)
                .setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
                .setFrom(0 * 1).setSize(1);


        phones = PhoneBlock.normalisePhones(phones);
        BoolQueryBuilder q = QueryBuilders.boolQuery();
        BoolQueryBuilder qw = QueryBuilders.boolQuery().minimumShouldMatch(1);
        if(accountId != null){
            q.must(QueryBuilders.termQuery("accountId", accountId));
        }
        for (String phone: phones) {
            qw.should(QueryBuilders.matchQuery("phones", phone));
        }
        q.must(qw);
        rb.setQuery(q);
        SearchResponse response = rb.execute().actionGet();

        Organisation org = null;
        for (SearchHit sh: response.getHits()) {
            String dataJson = sh.getSourceAsMap().get(E_DATAFIELD).toString();
            org = gson.fromJson(dataJson, Organisation.class);
            break;
        }

        return org;
    }

    public List<Organisation> list (Long accountId, Long userId, int page, int perPage,  Map<String, String> filter, Map<String, String> sort, String searchQuery) {

        List<Organisation> orgList = new ArrayList<>();

        SearchRequestBuilder rb = elasticClient.prepareSearch(E_INDEX)
                .setTypes(E_TYPE)
                .setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
                .setFrom(page * perPage).setSize(perPage);

        BoolQueryBuilder q = QueryBuilders.boolQuery();

        ParseResult pr = Query.parseContacts(searchQuery);
        List<FilterObject> rangeFilters = pr.filterList;

        if(accountId != null){
            q.must(QueryBuilders.matchQuery("accountId", accountId));
        }

        if (pr.query != null && pr.query.length() > 2) {
            q.must(QueryBuilders.matchQuery("tags", pr.query).operator(Operator.AND));
            q.should(QueryBuilders.matchQuery("name", pr.query).boost(8));
            q.should(QueryBuilders.matchQuery("main_office", pr.query).boost(4));
        }

        filter.forEach((k,v) -> {
            if (v != null && !v.equals("all")) {
                if (k.equals("typeCode")) {
                    if (v.equals("realtor")) {
                        q.must(QueryBuilders.termQuery("accountId", accountId));
                        q.must(QueryBuilders.termQuery(k, "realtor"));
                    } else if (v.equals("partner")) {
                        q.must(QueryBuilders.termQuery("accountId", accountId));
                        q.must(QueryBuilders.termQuery(k, "partner"));
                    } else if (v.equals("company")) {
                        q.must(QueryBuilders.termQuery("accountId", accountId));
                        q.must(QueryBuilders.termQuery(k, "company"));
                    } else if (v.equals("client")) {
                        q.must(QueryBuilders.termQuery("accountId", accountId));
                        q.must(QueryBuilders.termQuery(k, "client"));
                    } else if (v.contains("my")) {
                        q.must(QueryBuilders.termQuery("agentId", userId));
                    } else if(v.equals("accounts")){
                        q.must(QueryBuilders.termQuery(k, "company"));
                    } else{
                        q.must(QueryBuilders.termQuery("accountId", accountId));
                        q.must(QueryBuilders.termQuery(k, v));
                    }
                } else if (k.equals("changeDate") || k.equals("addDate")) {
                    long date = Long.parseLong(v);
                    // 86400 sec in 1 day
                    long ts = CommonUtils.getUnixTimestamp() - date * 86400;
                    q.must(QueryBuilders.rangeQuery(k).gte(ts));
                } else{  //tag, stateCode and any
                    q.must(QueryBuilders.termQuery(k, v));
                }
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

        /*if (searchQuery != null && searchQuery.length() > 0) {
            if(mustName)
                q.must(QueryBuilders.termQuery("name", searchQuery));
            else
                q.should(QueryBuilders.matchQuery("name", searchQuery));
            if(mustPhone)
                q.must(QueryBuilders.matchQuery("phones", searchQuery));
        }*/
        //logger.info("q " + q.toString());

        rb.setQuery(q);

        // execute

        SearchResponse response = rb.execute().actionGet();

        for (SearchHit sh: response.getHits()) {
            String dataJson = sh.getSourceAsMap().get(E_DATAFIELD).toString();
            orgList.add(gson.fromJson(dataJson, Organisation.class));
        }

        return orgList;
    }

    public Organisation getByPhone (Long account, List<String> phones, Long excl_id) {

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
        if(account != null){
            q.must(QueryBuilders.termQuery("accountId", account));
        } else{
            q.must(QueryBuilders.termQuery("typeCode", "company"));
        }
        if(excl_id != null)
            q.mustNot(QueryBuilders.termQuery("id", excl_id));
        rb.setQuery(q);
        SearchResponse response = rb.execute().actionGet();

        Organisation org = null;
        for (SearchHit sh: response.getHits()) {
            String dataJson = sh.getSourceAsMap().get(E_DATAFIELD).toString();
            org = gson.fromJson(dataJson, Organisation.class);
            break;
        }

        return org;
    }

    //пересчет рейтинга у контактов
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
        //Для каждого контакта высчитываем рейтинг и обновляем контакт
        for (SearchHit sh: response.getHits()) {
            String dataJson = sh.getSourceAsMap().get(E_DATAFIELD).toString();
            Organisation org = gson.fromJson(dataJson, Organisation.class);
            org.setRate(ratingService.getAverage(org.getPhoneBlock().getAsList(), "organisation"));
            try {
                org = save(org);
            } catch(Exception exp){}
        }
    }

    public Organisation get (long id) {

        //this.logger.info("get");

        Organisation result = null;

        GetResponse response = this.elasticClient.prepareGet(E_INDEX, E_TYPE, Long.toString(id)).get();

        String dataJson = response.getSourceAsMap().get(E_DATAFIELD).toString();
        result = gson.fromJson(dataJson, Organisation.class);

        return result;
    }

    public Organisation save (Organisation organisation) throws Exception {

        this.logger.info("save");
        organisation.setRate(ratingService.getAverage(organisation.getPhoneBlock().getAsList(), "organisation"));
        indexAccount(organisation);

        return organisation;
    }

    public Organisation delete (long id) {
        return null;
    }


    public void indexAccount(Organisation organisation) {

        Map<String, Object> json = new HashMap<String, Object>();

        if (organisation.getId() == null) {
            organisation.setId(CommonUtils.getSystemTimestamp());
            organisation.setAddDate(CommonUtils.getUnixTimestamp());
        }
        organisation.setChangeDate(CommonUtils.getUnixTimestamp());

        json.put("id", organisation.getId());
        json.put("accountId", organisation.getAccountId());
        json.put("orgRef", organisation.getOrgRef());

        String tags = CommonUtils.strNotNull(organisation.getName()).toLowerCase();
        json.put("name", tags);

        json.put("agentId", organisation.getAgentId());
        String agent_name = "";
        if(organisation.getAgent() != null){
            agent_name = CommonUtils.strNotNull(organisation.getAgent().getName()).toLowerCase().replaceAll("\"", "");
            json.put("agent", agent_name);
        }

        json.put("main_officeId", organisation.getMain_office_id());
        String org_name = "";
        if(organisation.getMain_office() != null){
            org_name = CommonUtils.strNotNull(organisation.getMain_office().getName()).toLowerCase().replaceAll("\"", "");
            json.put("main_office", org_name);
        }

        tags += " " + agent_name + " " + org_name + " ";
        json.put("tags", tags);

        if(!organisation.getId().equals(organisation.getAccountId()))
            organisation.setIsAccount(false);
        else
            organisation.setIsAccount(true);

        List<String> phoneArray = organisation.getPhoneBlock().getAsList();
        json.put("phones", String.join(" ", phoneArray));

        List<String> mailArray = organisation.getEmailBlock().getAsList();
        json.put("emails", String.join(" ", mailArray).replace("@",""));

        json.put("tag", organisation.getTag());
        json.put("changeDate", organisation.getChangeDate());
        json.put("addDate", organisation.getAddDate());

        if(organisation.getTypeCode() == null)
            organisation.setTypeCode("client");
        if(organisation.getStateCode() == null)
            organisation.setStateCode("raw");
        if(organisation.getGoverType() == null)
            organisation.setGoverType("main");


        json.put("typeCode", organisation.getTypeCode());
        json.put("stateCode", organisation.getStateCode());
        json.put("goverType", organisation.getGoverType());

        json.put(E_DATAFIELD, gson.toJson(organisation));

        IndexResponse response = this.elasticClient.prepareIndex(E_INDEX, E_TYPE, Long.toString(organisation.getId())).setSource(json).get();
    }
}
