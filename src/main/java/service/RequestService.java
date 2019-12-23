package service;

import auxclass.EmailBlock;
import auxclass.PhoneBlock;
import auxclass.UploadFile;
import auxclass.ValueRange;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import configuration.AppConfig;
import entity.Organisation;
import entity.Person;
import entity.User;
import entity.Request;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.geo.GeoPoint;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.*;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.CommonUtils;
import utils.FilterObject;
import utils.ParseResult;
import utils.Query;
import java.util.*;


public class RequestService {
    public class ListResult {
        long hitsCount;
        List<Request> list;
    }
    private final String E_INDEX = "ms-request";
    private final String E_TYPE = "requests";
    private final String E_DATAFIELD = "data";

    Logger logger = LoggerFactory.getLogger(RequestService.class);

    private final Client elasticClient;
    private final UserService userService;
    private final PersonService personService;
    private final OrganisationService organisationService;
    private final HistoryService historyService;

    Gson gson = new GsonBuilder().create();

    public RequestService (Client elasticClient,
                        UserService userService,
                        PersonService personService,
                        OrganisationService organisationService,
                        HistoryService historyService
    ) {
        this.elasticClient = elasticClient;
        this.userService = userService;
        this.personService = personService;
        this.organisationService = organisationService;
        this.historyService = historyService;
    }


    public ListResult list (long accountId, long userId, int page, int perPage, Map<String, Object> filter, Map<String, String> sort, String searchQuery, List<GeoPoint> geoSearchPolygon) {

        List<Request> requestList = new ArrayList<>();
        Map<String, String> queryParts = Query.process(searchQuery.toLowerCase());
        String requestStr = queryParts.get("req");

        ParseResult pr = Query.parse(requestStr);
        List<FilterObject> rangeFilters = pr.filterList;

        SearchRequestBuilder rb = elasticClient.prepareSearch(E_INDEX)
                .setTypes(E_TYPE)
                .setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
                .setFrom(page * perPage).setSize(perPage);

        BoolQueryBuilder q = QueryBuilders.boolQuery();

        if (geoSearchPolygon.size() > 0) {
            q.filter(QueryBuilders.geoPolygonQuery("location", geoSearchPolygon));
        }

        filter.forEach((k,v) -> {
            if(k.equals("source")){
                if(v.equals("all")){
                    q.must(QueryBuilders.termQuery("stageCode", "listing"));
                } else{
                    q.must(QueryBuilders.termQuery("accountId", accountId));
                }
            } else if(k.equals("attachment") && v.equals("my")){
                q.must(QueryBuilders.termQuery("agentId", userId));
            } else if(v != null && !v.equals("all")) {
                if(k.equals("changeDate") || k.equals("addDate")) {
                    long date = Long.parseLong(v.toString());
                    long ts = CommonUtils.getUnixTimestamp() - date * 86400; // 86400 sec in 1 day
                    q.must(QueryBuilders.rangeQuery(k).gte(ts));
                } else if(k.equals("contactType") || k.equals("tag") || k.equals("isMiddleman")) {
                    q.must(QueryBuilders.termQuery("accountId", accountId));
                    q.must(QueryBuilders.termQuery(k, v));
                } else {
                    q.must(QueryBuilders.termQuery(k, v));
                }
            }
        });


        rangeFilters.forEach(fltr -> {
            if(fltr.arrayVal != null && fltr.arrayVal.size() > 0){
                BoolQueryBuilder qw = QueryBuilders.boolQuery().minimumShouldMatch(1);
                if(fltr.fieldName.equals("phones") ){
                    for (String phone: fltr.arrayVal) {
                        qw.should(QueryBuilders.matchQuery(fltr.fieldName, phone));
                    }
                } if(fltr.fieldName.equals("emails") ){
                    for (String mail: fltr.arrayVal) {
                        qw.should(QueryBuilders.matchQuery(fltr.fieldName, mail.replaceAll("@", "")));
                    }
                } /*else{
                    for (String val: fltr.arrayVal) {
                        qw.should(QueryBuilders.termQuery(fltr.fieldName, val));
                    }
                }*/
                q.must(qw);
            } /*else if(fltr.exactVal != null) {
                q.must(QueryBuilders.termQuery(fltr.fieldName, fltr.exactVal));
            } else {
                if (fltr.lowerVal != null && fltr.upperVal != null) {
                    q.must(QueryBuilders.rangeQuery(fltr.fieldName).gte(fltr.lowerVal).lte(fltr.upperVal));
                } else if (fltr.lowerVal != null) {
                    q.must(QueryBuilders.rangeQuery(fltr.fieldName).gte(fltr.lowerVal));
                } else if (fltr.upperVal != null) {
                    q.must(QueryBuilders.rangeQuery(fltr.fieldName).lte(fltr.upperVal));
                }
            }*/
        });

        if (pr.query != null && pr.query.length() > 2) {
            q.must(QueryBuilders.matchQuery("tags", pr.query).operator(Operator.AND));
            q.should(QueryBuilders.matchQuery("request", pr.query));
            q.should(QueryBuilders.matchQuery("contactName", pr.query).boost(8));
            q.should(QueryBuilders.matchQuery("orgName", pr.query).boost(6));
            q.should(QueryBuilders.matchQuery("agentName", pr.query).boost(4));
            q.should(QueryBuilders.matchQuery("description", pr.query));
            q.should(QueryBuilders.matchQuery("costInfo", pr.query));
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
        ListResult r = new ListResult();
        r.hitsCount = response.getHits().getTotalHits();
        for (SearchHit sh: response.getHits()) {
            String dataJson = sh.getSourceAsMap().get(E_DATAFIELD).toString();
            Request req = gson.fromJson(dataJson, Request.class);

            req.setPersonId(sh.getSourceAsMap().get("personId") != null ? Long.parseLong(sh.getSourceAsMap().get("personId").toString()): null);
            if(req.getPersonId() == null) req.setPerson(null);
            req.setCompanyId(sh.getSourceAsMap().get("companyId") != null ? Long.parseLong(sh.getSourceAsMap().get("companyId").toString()): null);
            if(req.getCompanyId() == null) req.setCompany(null);
            req.setAgentId(sh.getSourceAsMap().get("agentId") != null ? Long.parseLong(sh.getSourceAsMap().get("agentId").toString()): null);
            if(req.getAgentId() == null) req.setAgent(null);

            if(req.getPersonId() != null){
                req.setPerson(personService.get(req.getPersonId()));
            } else if(req.getCompanyId() != null){
                req.setCompany(organisationService.get(req.getCompanyId()));
            }
            if(req.getAgentId() != null){
                req.setAgent(userService.get(req.getAgentId()));
            }

            if(!req.getAccountId().equals(accountId)){
                req.setTag(null);
                if(req.getAgent() != null){
                    Person pers = personService.getByPhone(req.getAgent().getPhoneBlock().getAsList(), accountId);

                    if(pers != null){
                        req.setPerson(pers);
                        req.setPersonId(pers.getId());
                    } else{
                        req.setPerson(req.getAgent().toPerson());
                        req.setPersonId(req.getAgent().getId());
                    }
                } else{
                    req.setPerson(null);
                    req.setPersonId(null);
                }
                req.setAgentId(null);
                req.setAgent(null);
            }
            requestList.add(req);
        }
        r.list = requestList;
        return r;
    }

    public float checkOffer (Long accountId, Long offerId, String offerTypeCode, String searchQuery, List<GeoPoint> geoSearchPolygon) {

        /*this.logger.info("check offer");

        Map<String, String> queryParts = Query.process(searchQuery);
        String request = queryParts.get("req");
        String excl = queryParts.get("excl");
        String near = queryParts.get("near");
        ParseResult pr = Query.parse(request);
        List<FilterObject> rangeFilters = pr.filterList;

        SearchRequestBuilder rb = elasticClient.prepareSearch(E_INDEX)
                .setTypes("offer")
                .setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
                .setFrom(0).setSize(10);


        BoolQueryBuilder q = QueryBuilders.boolQuery();

        q.must(QueryBuilders.termQuery("accountId", accountId));
        q.must(QueryBuilders.termQuery("id", offerId));
        q.must(QueryBuilders.termQuery("offerTypeCode", offerTypeCode));

        if (geoSearchPolygon.size() > 0) {
            q.filter(QueryBuilders.geoPolygonQuery("location", geoSearchPolygon));
        }

        if (excl != null && excl.length() > 0) {
            q.mustNot(QueryBuilders.matchQuery("title", excl));
            q.mustNot(QueryBuilders.matchQuery("address_ext", excl));
            q.mustNot(QueryBuilders.matchQuery("spec", excl));
            q.mustNot(QueryBuilders.matchQuery("description", excl));
        }

        rangeFilters.forEach(fltr -> {
            if (fltr.exactVal != null) {
                q.must(QueryBuilders.termQuery(fltr.fieldName, fltr.exactVal));
            } else {
                if (fltr.lowerVal != null) {
                    q.must(QueryBuilders.rangeQuery(fltr.fieldName).gte(fltr.lowerVal));
                }

                if (fltr.upperVal != null) {
                    q.must(QueryBuilders.rangeQuery(fltr.fieldName).lte(fltr.upperVal));
                }
            }
        });

        if (pr.query != null && pr.query.length() > 0) {
            q.should(QueryBuilders.matchQuery("title", pr.query).boost(8));
            q.should(QueryBuilders.matchQuery("address_ext", pr.query).boost(4));
            q.should(QueryBuilders.matchQuery("spec", pr.query).boost(2));
            q.should(QueryBuilders.matchQuery("description", pr.query));
        }

        rb.setQuery(q);

        SearchResponse response = rb.execute().actionGet();


        if (response.getHits().getTotalHits() > 0) {
            return response.getHits().getMaxScore();
        }

        return 0.0f;*/
        return 0f;
    }

    public List<Request> listForOffer (Long accountId, int page, int perPage, long offerId) {

        /*logger.info("list for offer");

        List<Request> requestList = new ArrayList<>();

        List<Request> rqList = list(accountId, 0, 0, 100, null, null, null, null).list;

        // перебрать и проверить подходит ли объект
        HashMap<Float, Request> tMap = new HashMap<Float, Request>();
        rqList.forEach(rq -> {
            ArrayList<GeoPoint> gpa = new ArrayList<>();
            for (auxclass.GeoPoint p : rq.getSearchArea()) {
                gpa.add(new GeoPoint(p.lat, p.lon));
            }
            if (rq.getOfferTypeCode() != null) {
                float score = checkOffer(accountId, offerId, rq.getOfferTypeCode(), rq.getRequest(), gpa);
                if (score > 2.1f) {
                    if (tMap.get(score) != null) {
                        score += 0.00001;
                    }
                    tMap.put(score, rq);
                }
            }
        });

        // отсортировать подходящие по оценке
        SortedSet<Float> scores = new TreeSet<Float>(tMap.keySet());
        for (Float s : scores) {
            Request r = tMap.get(s);
            requestList.add(0, r);
        }

        return requestList;*/
        return null;
    }

    public Request get (long id) {
        Request result;
        GetResponse response = this.elasticClient.prepareGet(E_INDEX, E_TYPE, Long.toString(id)).get();
        String dataJson = response.getSourceAsMap().get(E_DATAFIELD).toString();
        result = gson.fromJson(dataJson, Request.class);
        return result;
    }

    public Request save (Request request) throws Exception {

        if (request.getAgentId() != null){
            request.setAgent(userService.get(request.getAgentId()));
        }

//        Map<String, String> queryParts = Query.process(request.getRequest().toLowerCase());
//        ParseResult pr = Query.parse(queryParts.get("req"));

//        List<FilterObject> rangeFilters = pr.filterList;
//        rangeFilters.forEach(filter -> {
//            switch (filter.fieldName) {
//                case "typeCode":
//                    request.setTypeCodes(filter.arrayVal.toArray(new String[filter.arrayVal.size()]));
//                    break;
//                case "ownerPrice":
//                    request.setBudget(new ValueRange(filter.exactVal, filter.lowerVal, filter.upperVal));
//                    break;
//                case "squareTotal":
//                    request.setSquare(new ValueRange(filter.exactVal, filter.lowerVal, filter.upperVal));
//                    break;
//            }
//        });
//        request.setParseRequest(rangeFilters);
        indexRequest(request);

        return request;
    }

    public RestStatus delete (String id) {
        DeleteResponse response = this.elasticClient.prepareDelete(this.E_INDEX, this.E_TYPE, id).get();
        return response.status();
    }

    public long deleteByQuery(Map<String, Object> filter){
        BoolQueryBuilder q = QueryBuilders.boolQuery();
        filter.forEach((k,v) -> {
            q.must(QueryBuilders.termQuery(k, v));
        });

        SearchResponse response = new DeleteByQueryRequestBuilder(elasticClient, DeleteByQueryAction.INSTANCE)
                .source(this.E_INDEX).filter(q).source().setTypes(this.E_TYPE).get();
        long deleted = response.getHits().getTotalHits();
        return deleted;
    }

    public long deleteWithFiles(Map<String, Object> filter){
        BoolQueryBuilder q = QueryBuilders.boolQuery();
        filter.forEach((k,v) -> q.must(QueryBuilders.termQuery(k, v)));

        SearchRequestBuilder rb = elasticClient.prepareSearch(E_INDEX)
                .setTypes(E_TYPE)
                .setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
                .setFrom(0).setSize(1000);
        rb.setQuery(q);

        SearchResponse response = rb.execute().actionGet();

        logger.info("for deleting: " + response.getHits().getTotalHits() + " requests");
        Long i = 0l;
        for (SearchHit sh: response.getHits()) {
            String dataJson = sh.getSourceAsMap().get(E_DATAFIELD).toString();
            Request req = gson.fromJson(dataJson, Request.class);
            try{
                delete(req.getId().toString());
                UploadFile.deleteDirectory(AppConfig.FILE_STORAGE_PATH + "docs/requests/"  + req.getAccountId() + "/" + req.getId());
                i++;
            } catch (Exception exp){
                continue;
            }
        }
        return i;
    }


    public void indexRequest(Request request) {
        request.preIndex();

        String agentName = null;
        String contactName = null;
        String contactType = null;
        Boolean isMiddleman = null;
        String orgName = null;
        Long orgId = null;
        PhoneBlock phones = new PhoneBlock();
        EmailBlock mails = new EmailBlock();

        User agent = null;
        Person person = null;
        Organisation org = null;

        if (request.getAgentId() != null) {
            agent = userService.get(request.getAgentId());
            agentName = agent.getName();
        }

        if (request.getPersonId() != null) {
            person = personService.get(request.getPersonId());

            contactName = person.getName();
            contactType = person.getTypeCode();
            isMiddleman = person.getIsMiddleman() || false;

            phones = person.getPhoneBlock();
            mails = person.getEmailBlock();

            if (person.getOrganisationId() != null) {
                Organisation org1 = organisationService.get(person.getOrganisationId());
                orgName = org1.getName();
                orgId = org1.getOrganisationId();
            }
        } else if(request.getCompanyId() != null){
            org = organisationService.get(request.getCompanyId());
            contactName = org.getName();
            contactType = org.getTypeCode();
            isMiddleman = org.getIsMiddleman() || false;
            phones = request.getCompany().getPhoneBlock();
            mails = request.getCompany().getEmailBlock();
        }

        Map<String, Object> json = new HashMap<String, Object>();
        json.put("id", request.getId());
        json.put("accountId", request.getAccountId());

        json.put("description", CommonUtils.replaceSymb(request.getDescription()));
        json.put("costInfo", CommonUtils.replaceSymb(request.getCostInfo()));
        json.put("agentName", CommonUtils.replaceSymb(agentName));
        json.put("contactName", CommonUtils.replaceSymb(contactName));
        json.put("orgName", CommonUtils.replaceSymb(orgName));
        json.put("tags", CommonUtils.replaceSymb(json.get("contactName") + " " + json.get("orgName") + " " + json.get("agentName") + " "
                + json.get("description") + " " + json.get("costInfo")));

        json.put("phones", String.join(" ", phones.getAsList()));
        json.put("emails", String.join(" ", mails.getAsList()).replace("@",""));

        json.put("isMiddleman", isMiddleman ? "middleman" : "owner");
        json.put("contactType", CommonUtils.replaceSymb(contactType));

        json.put("offerTypeCode", request.getOfferTypeCode());
        json.put("stageCode", request.getStageCode());
        json.put("agentId", request.getAgentId());
        json.put("personId", request.getPersonId());
        json.put("companyId", request.getCompanyId());
        json.put("orgId", orgId);

        json.put("addDate", request.getAddDate());
        json.put("changeDate", request.getChangeDate());
        json.put("tag", request.getTag());
        json.put("newBuilding", request.getNewBuilding());
        json.put("encumbrance", request.getEncumbrance());

        if(request.getOfferTypeCode() == "rent"){
            json.put("deposit", request.getDeposit());
            json.put("utilityBills", request.getUtilityBills());
            json.put("commission", request.getCommission());
            json.put("counters", request.getCounters());

            json.put("complete", request.getConditions().isComplete());
            json.put("living_room_furniture", request.getConditions().isLiving_room_furniture());
            json.put("kitchen_furniture", request.getConditions().isKitchen_furniture());
            json.put("couchette", request.getConditions().isCouchette());
            json.put("bedding", request.getConditions().isBedding());
            json.put("dishes", request.getConditions().isDishes());
            json.put("refrigerator", request.getConditions().isRefrigerator());
            json.put("washer", request.getConditions().isWasher());
            json.put("microwave_oven", request.getConditions().isMicrowave_oven());
            json.put("air_conditioning", request.getConditions().isAir_conditioning());
            json.put("dishwasher", request.getConditions().isDishwasher());
            json.put("tv", request.getConditions().isTv());
            json.put("with_animals", request.getConditions().isWith_animals());
            json.put("with_children", request.getConditions().isWith_children());
        } else{
            json.put("cash", request.getNewBuilding());
            json.put("mortgage", request.getMortgage());
            json.put("certificate", request.getNewBuilding());
            json.put("maternalCapital", request.getMortgage());
        }

        request.setPerson(null);
        request.setAgent(null);
        request.setCompany(null);

        json.put(E_DATAFIELD, gson.toJson(request));

        IndexResponse response = this.elasticClient.prepareIndex(E_INDEX, E_TYPE, Long.toString(request.getId())).setSource(json).get();

        request.setPerson(person);
        request.setAgent(agent);
        request.setCompany(org);
    }

    public void updateRequest(String script_text, Map<String, Object> filter) {
        UpdateByQueryRequestBuilder ubqrb = UpdateByQueryAction.INSTANCE.newRequestBuilder(this.elasticClient);
        script_text += "ctx._source.tags = ctx._source.address + ' ' + ctx._source.contactName + ' ' + " +
                "ctx._source.orgName + ' ' + " + "ctx._source.agentName + ' ' + ctx._source.description;";

        Script script = new Script(script_text);
        BoolQueryBuilder q = QueryBuilders.boolQuery();
        filter.forEach((k, v) -> {
            q.must(QueryBuilders.termQuery(k, v));
        });
        ubqrb.source(E_INDEX).filter(q).script(script).source().setTypes(E_TYPE);

        BulkByScrollResponse response = ubqrb.execute().actionGet();
    }
}
