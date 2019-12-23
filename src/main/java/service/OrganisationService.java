package service;

import auxclass.ErrorMsg;
import com.google.gson.Gson;
import entity.Account;
import entity.Person;
import entity.User;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.geo.GeoPoint;
import org.elasticsearch.index.query.*;
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
    private final PersonService personService;
    Gson gson = new Gson();

    public OrganisationService (Client elasticClient,
                                PersonService personService,
                                RatingService ratingService) {
        this.ratingService = ratingService;
        this.personService = personService;
        this.elasticClient = elasticClient;
    }

    public List<ErrorMsg> check (Organisation org) {
        List<ErrorMsg> errors = new LinkedList<>();
        try{
            /*if(user.getAccountId() == null){
                errors.add("001:account not specified");
            }*/
            if((org.getEmailBlock() == null && org.getPhoneBlock() == null) || org.getEmailBlock().getAsList().size() + org.getPhoneBlock().getAsList().size() == 0 ){
                errors.add(new ErrorMsg("200: No phones or emails|", "Не указан номер телефона или емайл", "OrganisationError", 1));
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

                for (SearchHit sh: response.getHits()) {
                    String dataJson = sh.getSourceAsMap().get(E_DATAFIELD).toString();
                    errors.add(new ErrorMsg("300: Organisation with such email or phone already exists",
                            "Контакт с таким телефоном или емайл уже существует", "OrganisationError", 1, dataJson));
                }
                for (SearchHit sh: response.getHits()) {
                    String dataJson = sh.getSourceAsMap().get(E_DATAFIELD).toString();
                }
            }
        } catch(Exception exp){
            errors.add(new ErrorMsg("900: System error | " + ExceptionUtils.getStackTrace(exp),"Системная ошибка", "OrganisationError",0));
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

    public List<Organisation> list (Long accountId, Long userId, int page, int perPage,  Map<String, Object> filter, Map<String, String> sort, String searchQuery) {

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

        filter.forEach((k,v) -> {
            if (v != null && !v.equals("all")) {
                if (k.equals("typeCode")) {
                    if (v.equals("company")) {
                        q.must(QueryBuilders.termQuery("accountId", accountId));
                        q.must(QueryBuilders.termQuery(k, "company"));
                    } else if (v.equals("my")) {
                        q.must(QueryBuilders.termQuery("agentId", userId));
                    } else if(v.equals("accounts")){
                        q.must(QueryBuilders.termQuery("ourCompany", 1));
                    } else{
                        q.must(QueryBuilders.termQuery("accountId", accountId));
                        q.must(QueryBuilders.termQuery(k, v));
                    }
                } else if (k.equals("changeDate") || k.equals("addDate")) {
                    long date = Long.parseLong(v.toString());
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
            //rb.addSort(SortBuilders.fieldSort("changeDate").order(SortOrder.DESC));
            if (pr.query != null && pr.query.length() > 2) {
                BoolQueryBuilder qw = QueryBuilders.boolQuery().minimumShouldMatch(1);
                qw.should(QueryBuilders.matchQuery("name", pr.query));
                qw.should(QueryBuilders.matchQuery("main_office", pr.query));
                q.must(qw);
            }
        } else {
            if (pr.query != null && pr.query.length() > 2) {
                q.must(QueryBuilders.matchQuery("tags", pr.query).operator(Operator.AND));
                q.should(QueryBuilders.matchQuery("name", pr.query).boost(8));
                q.should(QueryBuilders.matchQuery("main_office", pr.query).boost(4));
            }
            sort.forEach((k, v) -> {
                if (v.equals("ASC")) {
                    rb.addSort(SortBuilders.fieldSort(k).order(SortOrder.ASC));
                } else if (v.equals("DESC")) {
                    rb.addSort(SortBuilders.fieldSort(k).order(SortOrder.DESC));
                }
            });
        }

        this.logger.info(q.toString());
        rb.setQuery(q);

        // execute

        SearchResponse response = rb.execute().actionGet();

        for (SearchHit sh: response.getHits()) {
            String dataJson = sh.getSourceAsMap().get(E_DATAFIELD).toString();
            Organisation org = gson.fromJson(dataJson, Organisation.class);
            org.setContactId(sh.getSourceAsMap().get("contactId") != null ? Long.parseLong(sh.getSourceAsMap().get("contactId").toString()): null);
            if(org.getContactId() == null) org.setContact(null);
            org.setMain_office_id(sh.getSourceAsMap().get("mainOfficeId") != null ? Long.parseLong(sh.getSourceAsMap().get("mainOfficeId").toString()): null);
            if(org.getMain_office_id() == null) org.setMain_office(null);
            org.setAgentId(sh.getSourceAsMap().get("agentId") != null ? Long.parseLong(sh.getSourceAsMap().get("agentId").toString()): null);
            if(org.getAgentId() == null) org.setAgent(null);
            if(!org.getAccountId().equals(accountId))
                org.setTag(null);
            orgList.add(org);
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
        //getCoordsDistrict(organisation);
        indexAccount(organisation);

        return organisation;
    }

    private void getCoordsDistrict(Organisation org) {

        assert (org.getAddressBlock() != null);

        String addressBlock = org.getAddressBlock().getAsString();

        if (addressBlock.length() > 0) {
            Double[] latLon = GeoUtils.getCoordsByAddr(addressBlock);
            if (latLon != null) {
                org.setLocation(new auxclass.GeoPoint(latLon[0], latLon[1]));

                List<String> districts = GeoUtils.getLocationDistrict(latLon[0], latLon[1]);
                if (!districts.isEmpty()) {
                    org.getAddressBlock().setArea(districts.get(0));
                    if (districts.size() > 1){
                        org.getAddressBlock().setAdmArea(districts.get(1));
                    }
                }
            }
        }
    }

    public RestStatus delete (String id) {
        DeleteResponse response = this.elasticClient.prepareDelete(this.E_INDEX, this.E_TYPE, id).get();
        return response.status();
    }

    public void indexAccount(Organisation organisation) {

        Map<String, Object> json = new HashMap<String, Object>();
        organisation.preIndex();

        String name = CommonUtils.replaceSymb(organisation.getName());
        String agent_name = "";
        String mainOrg_name = "";
        String contact_name = "";
        String tags = "";
        Person contact = null;
        Organisation mainOrg = null;

        if(organisation.getAgentId() != null)
            agent_name = CommonUtils.replaceSymb(organisation.getAgent().getName());
        if(organisation.getMain_office_id() != null){
            mainOrg = get(organisation.getMain_office_id());
            mainOrg_name = CommonUtils.replaceSymb(mainOrg.getName());
        }

        if(organisation.getContactId() != null){
            contact = personService.get(organisation.getContactId());
            contact_name = CommonUtils.replaceSymb(contact.getName());
        }

        tags += name + " " + agent_name + " " + mainOrg_name + " " + contact_name;

        json.put("id", organisation.getId());
        json.put("accountId", organisation.getAccountId());
        json.put("orgRef", organisation.getOrgRef());
        json.put("name", name);

        json.put("tags", tags);
        json.put("agentId", organisation.getAgentId());
        json.put("agent", agent_name);
        json.put("mainOfficeId", organisation.getMain_office_id());
        json.put("mainOffice", mainOrg_name);
        json.put("contactId", organisation.getContactId());
        json.put("contactName", contact_name);
        json.put("isMiddleman", organisation.getIsMiddleman() ? "middleman" : "owner");

        List<String> phoneArray = organisation.getPhoneBlock().getAsList();
        json.put("phones", String.join(" ", phoneArray));

        List<String> mailArray = organisation.getEmailBlock().getAsList();
        json.put("emails", String.join(" ", mailArray).replace("@",""));
        json.put("ourCompany", organisation.getOurCompany() ? 1 : 0);
        json.put("tag", organisation.getTag());
        json.put("changeDate", organisation.getChangeDate());
        json.put("addDate", organisation.getAddDate());

        json.put("isAccount", organisation.getIsAccount());
        json.put("typeCode", organisation.getTypeCode());
        json.put("stateCode", organisation.getStateCode());
        json.put("goverType", organisation.getGoverType());
        // geo search
        if (organisation.getLocation() != null) {
            json.put("location", new GeoPoint(organisation.getLocation().getAsString()).geohash());
        }

        organisation.setMain_office_id(null);
        organisation.setContact(null);

        json.put(E_DATAFIELD, gson.toJson(organisation));

        organisation.setMain_office(mainOrg);
        organisation.setContact(contact);

        IndexResponse response = this.elasticClient.prepareIndex(E_INDEX, E_TYPE, Long.toString(organisation.getId())).setSource(json).get();
    }

    public void updateOrganisation(String script_text, Map<String, Object> filter) {
        UpdateByQueryRequestBuilder ubqrb = UpdateByQueryAction.INSTANCE.newRequestBuilder(this.elasticClient);
        script_text += "ctx._source.tags = ctx._source.name + ' ' + ctx._source.agent + ' ' + " +
                "ctx._source.mainOffice + ' ' + " + "ctx._source.contactName;";

        Script script = new Script(script_text);
        BoolQueryBuilder q = QueryBuilders.boolQuery();
        filter.forEach((k, v) -> {
            q.must(QueryBuilders.termQuery(k, v));
        });
        ubqrb.source(E_INDEX).filter(q).script(script).source().setTypes(E_TYPE);

        BulkByScrollResponse response = ubqrb.execute().actionGet();
    }
}
