package service;

import auxclass.*;
import entity.*;
import com.google.gson.*;

import configuration.AppConfig;

import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.geo.GeoPoint;
import org.elasticsearch.index.query.*;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.elasticsearch.index.reindex.UpdateByQueryRequestBuilder;
import org.elasticsearch.index.reindex.UpdateByQueryAction;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.*;

import static utils.CommonUtils.getUnixTimestamp;

public class OfferService {

    public class ListResult {
        long hitsCount = 0l;
        List<Offer> list =  new LinkedList<>();
    }

    private final String E_INDEX = "ms_offer";
    private final String E_TYPE = "offers";
    private final String E_DATAFIELD = "data";

    Logger logger = LoggerFactory.getLogger(OfferService.class);
    private final Client elasticClient;
    private final UserService userService;
    private final PersonService personService;
    private final OrganisationService organisationService;
    private final HistoryService historyService;

    Gson gson = new GsonBuilder().create();

    HashMap<String, String> dTypeCode = new HashMap<>();
    HashMap<String, String> dApScheme = new HashMap<>();
    HashMap<String, String> dBalcony = new HashMap<>();
    HashMap<String, String> dBathroom = new HashMap<>();
    HashMap<String, String> dCondition = new HashMap<>();
    HashMap<String, String> dHouseType = new HashMap<>();
    HashMap<String, String> dRoomScheme = new HashMap<>();

    public OfferService (Client elasticClient,
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

        dTypeCode.put("share", "доля");
        dTypeCode.put("room", "комната");
        dTypeCode.put("apartment", "квартира");
        dTypeCode.put("house", "дом");
        dTypeCode.put("dacha", "дача");
        dTypeCode.put("cottage", "коттедж");
        dTypeCode.put("townhouse", "таунхаус");
        dTypeCode.put("duplex", "дуплекс");

        dTypeCode.put("garden_land", "садовый участок");
        dTypeCode.put("cultivate_land", "огородный участок");
        dTypeCode.put("dacha_land", "дачный участок");

        dTypeCode.put("hotel", "отель");
        dTypeCode.put("restaurant", "ресторан");
        dTypeCode.put("сafe", "кафе");
        dTypeCode.put("sport_building", "спортивное сооружение");
        dTypeCode.put("shop", "магазин");
        dTypeCode.put("shops_center", "торговый центр");
        dTypeCode.put("shop_entertainment", "торгово развлекательный комплекс");
        dTypeCode.put("cabinet", "кабинет");
        dTypeCode.put("office_space", "офисное помещение");
        dTypeCode.put("office_building", "офисное здание");
        dTypeCode.put("business_center", "бизнес центр");

        dTypeCode.put("manufacture_building", "производственное здание");
        dTypeCode.put("warehouse_space", "складское помещение");
        dTypeCode.put("industrial_enterprice", "промышленное предприятие");
        dTypeCode.put("other", "другое");

        /*dTypeCode.put("apartment_small", "Малосемейка");
        dTypeCode.put("apartment_new", "Новостройка");*/

        /*dApScheme.put(1, "Индивидуальная");
        dApScheme.put(2, "Новая");
        dApScheme.put(3, "Общежитие");
        dApScheme.put(4, "Сталинка");
        dApScheme.put(5, "Улучшенная");
        dApScheme.put(6, "Хрущевка");


        dBalcony.put(1, "без балкона");
        dBalcony.put(2, "балкон");
        dBalcony.put(3, "лоджия");
        dBalcony.put(4, "2 балкона");
        dBalcony.put(5, "2 лоджии");
        dBalcony.put(6, "балкон и лоджия");
        dBalcony.put(7, "балкон застеклен");
        dBalcony.put(8, "лоджия застеклена");*/

        dBathroom.put("no", "без удобств");
        dBathroom.put("splited", "раздельный санузел");
        dBathroom.put("combined", "совмещенный");

        dCondition.put("rough", "после строителей");
        dCondition.put("social", "социальный ремонт");
        dCondition.put("repaired", "сделан ремонт");
        dCondition.put("designer", "дизайнерский ремонт");
        dCondition.put("need", "требуется ремонт");
        dCondition.put("euro", "евроремонт");

        dHouseType.put("brick", "кирпичный");
        dHouseType.put("panel", "панельный");
        dHouseType.put("monolithic", "монолит");
        dHouseType.put("monolithic_brick", "монолитнокирпичный");
        dHouseType.put("wood", "деревянный");
        dHouseType.put("cinder block", "шлакоблочный");

        dRoomScheme.put("free", "свободная");
        dRoomScheme.put("separate", "раздельные");
        dRoomScheme.put("adjoin_separate", "смежно-раздельные");
        dRoomScheme.put("adjoining", "смежные");
        dRoomScheme.put("studio", "студия");
    }


    public ListResult list (Long accountId, Long userId, int page, int perPage, Map<String, String> filter, Map<String, String> sort, String searchQuery, List<GeoPoint> geoSearchPolygon) {
        ListResult r = new ListResult();

        List<Offer> offerList = new ArrayList<>();
        Map<String, String> queryParts = Query.process(searchQuery.toLowerCase());
        String request = queryParts.get("req");
        String excl = queryParts.get("excl");
        String near = queryParts.get("near");

        ParseResult pr = Query.parse(request);
        List<FilterObject> rangeFilters = pr.filterList;

        SearchRequestBuilder rb = elasticClient.prepareSearch(E_INDEX)
                .setTypes(E_TYPE)
                .setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
                .setFrom(page * perPage).setSize(perPage);


        BoolQueryBuilder q = QueryBuilders.boolQuery();

        if (geoSearchPolygon.size() > 0) {
            q.filter(QueryBuilders.geoPolygonQuery("location", geoSearchPolygon));
        }

        if (excl != null && excl.length() > 0) {
            q.mustNot(QueryBuilders.matchQuery("address_ext", excl));
            q.mustNot(QueryBuilders.matchQuery("spec", excl));
            q.mustNot(QueryBuilders.matchQuery("description", excl));
        }
        filter.forEach((k,v) -> {
            if (k.equals("stateCode")) {
                if (v != null && !v.equals("all")) {
                    q.must(QueryBuilders.termQuery(k, v));
                }
            } else if (k.equals("contactType") && v != null) {
                if (v.equals("all")) {
                    BoolQueryBuilder qw = QueryBuilders.boolQuery().minimumShouldMatch(1);
                    //q.must(QueryBuilders.termQuery("agentId", v));
                    qw.should(QueryBuilders.termQuery("accountId", accountId));
                    qw.should(QueryBuilders.termQuery("stateCode", "listing"));
                    q.must(qw);
                } else if(v.equals("realtor") || v.equals("partner") || v.equals("client")){
                    BoolQueryBuilder qw = null;
                    List<Person> pers = personService.list(accountId,null,0, 100,
                                            new HashMap<String, String>() {{ put("typeCode", v); put("userRef", "exist");}},null, "" );
                    //сформируем запрос по объектам из чужой базы
                    BoolQueryBuilder preqw = QueryBuilders.boolQuery();
                    this.logger.info("pers.size() " + pers.size());
                    if(pers.size() > 0){
                        qw = QueryBuilders.boolQuery();
                        for (Person person: pers) {
                            if(person.getUserRef() != null) preqw.should(QueryBuilders.termQuery("agentId", person.getUserRef()));
                        }
                        qw.mustNot(QueryBuilders.termQuery("accountId", accountId));
                        qw.must(QueryBuilders.termQuery("stateCode", "listing"));
                        qw.must(preqw);
                    }
                    preqw = QueryBuilders.boolQuery();
                    preqw.must(QueryBuilders.termQuery("accountId", accountId));
                    preqw.must(QueryBuilders.termQuery("contactType", v));
                    if(qw != null)
                        q.should(qw).should(preqw).minimumShouldMatch(1);
                    else q.must(preqw);
                } else if(v.equals("private")){
                    q.must(QueryBuilders.termQuery("accountId", accountId));
                    q.must(QueryBuilders.termQuery("contactType", "owner"));
                } else if (v.equals("company")) {
                    q.must(QueryBuilders.termQuery("accountId", accountId));
                } else if (v.contains("my")) {
                    String val = v.replace("my=", "");
                    q.must(QueryBuilders.termQuery("accountId", accountId));
                    q.must(QueryBuilders.termQuery("agentId", userId));
                } else {
                    q.must(QueryBuilders.termQuery(k, v));
                }
            } else if (k.equals("tag")) {
                if (v != null) {
                    q.must(QueryBuilders.termQuery("accountId", accountId));
                    q.must(QueryBuilders.termQuery(k, v));
                }
            } else {
                if (v != null && !v.equals("all")) {
                    if (k.equals("changeDate") || k.equals("addDate")) {
                        long date = Long.parseLong(v);
                        // 86400 sec in 1 day
                        long ts = CommonUtils.getUnixTimestamp() - date * 86400;
                        q.must(QueryBuilders.rangeQuery(k).gte(ts));
                    } else if(k.equals("typeCode")){
                        String[] codes = v.split(",");
                        BoolQueryBuilder bq = QueryBuilders.boolQuery();
                        for(String code: codes) {
                            bq.should(QueryBuilders.termQuery("typeCode", code));
                        }
                        q.must(bq);
                    } else {
                        q.must(QueryBuilders.termQuery(k, v));
                    }
                }
            }
        });

        rangeFilters.forEach(fltr -> {
            if(fltr.arrayVal != null && fltr.arrayVal.size() > 0){
                BoolQueryBuilder qw = QueryBuilders.boolQuery().minimumShouldMatch(1);
                if(fltr.fieldName.equals("phones")){
                    Person pers = personService.getByPhone(fltr.arrayVal, accountId);
                    Organisation org = organisationService.getByPhone(fltr.arrayVal, accountId);
                    User user = userService.getByPhone(fltr.arrayVal);
                    if(pers != null){
                        qw.should(QueryBuilders.termQuery("personId", pers.getId()));
                    }
                    if(org != null){
                        qw.should(QueryBuilders.termQuery("companyId", org.getId()));
                    }
                    if(user != null){
                        qw.should(QueryBuilders.termQuery("personId", user.getId()));
                        qw.should(QueryBuilders.termQuery("agentId", user.getId()));
                    }
                    if(user == null && pers == null && org == null){
                        qw.must(QueryBuilders.termQuery("accountId", 0));
                    }
                } else{
                    for (String val: fltr.arrayVal) {
                        qw.should(QueryBuilders.termQuery(fltr.fieldName, val));
                    }
                }
                q.must(qw);
            } else if(fltr.exactVal != null) {
                q.must(QueryBuilders.termQuery(fltr.fieldName, fltr.exactVal));
            } else {
                if (fltr.lowerVal != null && fltr.upperVal != null) {
                    q.must(QueryBuilders.rangeQuery(fltr.fieldName).gte(fltr.lowerVal).lte(fltr.upperVal));
                } else if (fltr.lowerVal != null) {
                    q.must(QueryBuilders.rangeQuery(fltr.fieldName).gte(fltr.lowerVal));
                } else if (fltr.upperVal != null) {
                    q.must(QueryBuilders.rangeQuery(fltr.fieldName).lte(fltr.upperVal));
                }
            }
        });

        if (pr.query != null && pr.query.length() > 2) {
            q.must(QueryBuilders.matchQuery("tags", pr.query).operator(Operator.AND));
            q.should(QueryBuilders.matchQuery("address_ext", pr.query).boost(8));
            q.should(QueryBuilders.matchQuery("district", pr.query).boost(4));
            q.should(QueryBuilders.matchQuery("spec", pr.query).boost(2));
            q.should(QueryBuilders.matchQuery("description", pr.query));
            q.should(QueryBuilders.matchQuery("orgName", pr.query));
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

        r.hitsCount = response.getHits().getTotalHits();

        for (SearchHit sh: response.getHits()) {
            String dataJson = sh.getSourceAsMap().get(E_DATAFIELD).toString();
            Offer ofr = gson.fromJson(dataJson, Offer.class);

            if(!ofr.getAccountId().equals(accountId)){
                ofr.setTag(null);
                if(ofr.getAgent() != null){
                    Person pers = personService.getByPhone(ofr.getAgent().getPhoneBlock().getAsList(), accountId);

                    if(pers != null){
                        ofr.setPerson(pers);
                        ofr.setPersonId(pers.getId());
                        ofr.setPhoneBlock(pers.getPhoneBlock());
                        ofr.setEmailBlock(pers.getEmailBlock());
                    } else{
                        ofr.setPerson(ofr.getAgent().toPerson());
                        ofr.setPersonId(ofr.getAgent().getId());
                        ofr.setPhoneBlock(ofr.getAgent().getPhoneBlock());
                        ofr.setEmailBlock(ofr.getAgent().getEmailBlock());
                    }
                } else{
                    ofr.setPerson(null);
                    ofr.setPersonId(null);
                    ofr.setPhoneBlock(null);
                    ofr.setEmailBlock(null);
                }
                ofr.setAgentId(null);
                ofr.setAgent(null);
            } else{
                if(ofr.getPersonId() != null){
                    try{
                        ofr.setPerson(personService.get(ofr.getPersonId()));
                        ofr.setPhoneBlock(ofr.getPerson().getPhoneBlock());
                        ofr.setEmailBlock(ofr.getPerson().getEmailBlock());
                    } catch (NullPointerException npe){

                    }
                } else if(ofr.getCompanyId() != null)
                    ofr.setCompany(organisationService.get(ofr.getCompanyId()));
                    if(ofr.getCompany() != null && ofr.getCompany().getPhoneBlock() != null)
                        ofr.setPhoneBlock(ofr.getCompany().getPhoneBlock());
                    if(ofr.getCompany() != null && ofr.getCompany().getEmailBlock() != null)
                        ofr.setEmailBlock(ofr.getCompany().getEmailBlock());
            }
            offerList.add(ofr);
        }

        r.list = offerList;

        return r;
    }


    public ListResult listImport (Long accountId, Long userId, int page, int perPage, Map<String, String> filter, Map<String, String> sort, String searchQuery, List<GeoPoint> geoSearchPolygon)
    throws UnsupportedEncodingException{
        Map<String, String> queryParts = Query.process(searchQuery.toLowerCase());
        String request = queryParts.get("req");

        ParseResult pr = Query.parse(request);
        List<FilterObject> rangeFilters = pr.filterList;

        List<Offer> offerList = new ArrayList<>();
        Boolean isContact = new Boolean(false);
        Long hitsCount = 0L;
        ListResult r = new ListResult();
        r.hitsCount = 0;


        String agent = filter.get("contactType");
        filter.remove("contactType");
        List<Person> competCliList = new ArrayList<>();
        List<String> phones = new ArrayList<>();

        if (agent.contains("my")) {
            phones = userService.get(userId).getPhoneBlock().getAsList();
        } else if(agent.contains("company")){
            List<User> userList = userService.list(accountId, null, 0, 1000, new HashMap<>(), null, "");
            for(User usr : userList){
                phones.addAll(0, usr.getPhoneBlock().getAsList());
            }
        } else if(!agent.contains("all")){
            if(agent.contains("owner") || agent.contains("middleman")) {
                if (agent.contains("owner"))
                    filter.put("mediatorCompany", "false");
                else
                    filter.put("mediatorCompany", "true");
            /*} else if(agent.contains("partner")){
                temp_filtr.put("typeCode", "partner");
            } else if(agent.contains("realtor")){
                temp_filtr.put("typeCode", "realtor");
                filter.put("mediatorCompany", "true");
            } else if(agent.contains("owner")){
                temp_filtr.put("typeCode", "owner");
                filter.put("mediatorCompany", "false");

            }*/
                List<Person> clientList = personService.list(accountId, null, 0,
                        1000, new HashMap<String, String>() {{
                            put("typeCode", "realtor");
                        }}, null, "");
                for (Person psn : clientList) {
                    phones.addAll(0, psn.getPhoneBlock().getAsList());
                }
                List<Organisation> orgList = organisationService.list(accountId, null, 0,
                        1000, new HashMap<String, String>() {{
                            put("typeCode", "realtor");
                        }}, null, "");
                for (Organisation org : orgList) {
                    phones.addAll(0, org.getPhoneBlock().getAsList());
                }
            }
        }

        if(phones.size() > 0)
            rangeFilters.add(new FilterObject("phones", phones));

        String url = AppConfig.IMPORT_URL + "/api/offer/search?"
        + "query=" + URLEncoder.encode( pr.query, "UTF-8")
        + "&rangeFilters=" + gson.toJson(rangeFilters)
        + "&filter=" + gson.toJson(filter)
       // + "&offer_type=" + (filter.get("offerTypeCode") != null ? filter.get("offerTypeCode") : "")
        //+ "&change_date=" + filter.get("changeDate")
        //+ "&agent=" + URLEncoder.encode(agent, "UTF-8")
        + "&page=" + page
        + "&per_page=" + perPage
        + "&sort=" + gson.toJson(sort)
        + "&search_area=" + (geoSearchPolygon.size() > 0 ? gson.toJson(geoSearchPolygon) : "");

        this.logger.info(url);
        try {
            URL iurl = new URL(url);
            HttpURLConnection uc = (HttpURLConnection) iurl.openConnection();
            uc.connect();
            int status = uc.getResponseCode();
            switch (status) {
                case 200:
                case 201:
                    String jsonStr;
                    BufferedReader br = new BufferedReader(new InputStreamReader(uc.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line+"\n");
                    }
                    br.close();
                    jsonStr = sb.toString();

                    JsonObject jsonObject = new JsonParser().parse(jsonStr).getAsJsonObject();
                    JsonArray t = jsonObject.get("hits").getAsJsonArray();
                    hitsCount = jsonObject.get("total").getAsLong();
                    for(int i=0; i<t.size(); ++i){

                        String os = t.get(i).getAsJsonObject().get("_source").getAsJsonObject().get("data").toString().replace("\\\"", "\"").replace("\\\"", "\"");
                        os = os.substring(1, os.length()-1);
                        try {
                            Offer offer = gson.fromJson(os, Offer.class);
                            auxclass.Rating rat = new auxclass.Rating();
                            rat.setMap(new HashMap<String, Float>(){{put("average", 0f);}});
                            offer.setLocRating(rat);
                            offer.setOfferRaiting(rat);
                            offer.setLastSeenDate(0L);
                            Map<String, String> flt = new HashMap<String, String>(){{put("importId", ""+offer.getImportId());}};
                            List<Offer> coof = list(accountId, null, 0, 1, flt, null, "", Arrays.asList(new GeoPoint[0])).list;
                            if(coof.size() > 0){
                                offer.setOfferRef(coof.get(0).getId());
                            }
                            offer.setPerson(null);
                            offer.setCompany(null);

                            Person client = personService.getByPhone(offer.getPhoneBlock().getAsList(), accountId);

                            if(client != null){
                                /*List<History> history = this.historyService.list(0, 100, cli.getId(), "person", offer.getAddDate()*1000);
                                if(history.size() != 0){
                                        cli.setOrganisationId(history.get(0).getPerson().getOrganisationId());
                                        if(mainOrgList.size() > 0){
                                            boolean flag = false;
                                            for(Organisation mainOrg : mainOrgList){
                                                if(mainOrg.getId().equals(cli.getOrganisationId()))
                                                    flag = true;
                                            }
                                            if(!flag) {
                                                hitsCount--;
                                                continue;
                                            }
                                        }
                                }*/
                                /*if(filter.get("contactType").equals("private") && !cli.getTypeCode().equals("owner")){
                                        hitsCount--;
                                        continue;
                                } else if(filter.get("contactType").equals("realtor") && !cli.getTypeCode().equals("realtor")){
                                        hitsCount--;
                                        continue;
                                }
                                else if(cli.getOrganisationId() != null){
                                        cli.setOrganisation(organisationService.get(cli.getOrganisationId()));
                                }*/
                                offer.setPerson(client);
                                offer.setPersonId(client.getId());
                            } else {//if(cli == null && !filter.get("contactType").equals("private")){
                                Organisation org = organisationService.getByPhone(offer.getPhoneBlock().getAsList(), accountId);
                                if(org != null){

                                    /*if(mainOrgList.size() > 0){
                                        boolean flag = false;
                                        for(Organisation mainOrg : mainOrgList){
                                            if(mainOrg.getId().equals(org.getId()))
                                                flag = true;
                                        }
                                        if(!flag) {
                                            hitsCount--;
                                            continue;
                                        }
                                    }*/
                                    offer.setCompany(org);
                                    offer.setCompanyId(org.getId());
                                } else{
                                    User user = userService.getByPhone(offer.getPhoneBlock().getAsList());
                                    if(user != null) {
                                        offer.setAgent(user);
                                        offer.setAgentId(user.getId());
                                    }
                                }

                                /*if(org == null){

                                    for(User usr : userList){
                                        offer.setAgent(usr);
                                    }
                                }*/

                            }
                            offerList.add(offer);
                        } catch (Exception ex) {
                            ex.printStackTrace();
                            logger.error(ex.getMessage() + "\n" + os);
                        }
                    }
                    /*while(offerList.size() == 0 && hitsCount != 0){

                        return this.listImport(accountId, page + 1, perPage, filter, sort, searchQuery, geoSearchPolygon);
                    }*/

            }
        } catch (Exception ex) {
            ex.printStackTrace();
            logger.error(ex.getMessage());
            return null;
        }

        r.hitsCount = hitsCount;
        r.list = offerList;

        return r;
    }


    public ListResult listSimilar (Long accountId, int page, int perPage, long id) {

        this.logger.info("list similar");

        List<Offer> offerList = new ArrayList<>();

        Offer offer = get(id);

        SearchRequestBuilder rb = elasticClient.prepareSearch(E_INDEX)
                .setTypes(E_TYPE)
                .setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
                .setFrom(page * perPage).setSize(perPage);

        BoolQueryBuilder q = QueryBuilders.boolQuery();

        q.must(QueryBuilders.termQuery("accountId", accountId));
        q.must(QueryBuilders.termQuery("typeCode", offer.getTypeCode()));
        q.mustNot(QueryBuilders.termQuery("id", offer.getId()));

        /*if (offer.getHouseType() != null && offer.getHouseType() > 0) {
            q.must(QueryBuilders.termQuery("houseType", dHouseType.get(offer.getHouseTypeId())));
        }*/

        if (offer.getRoomsCount() != null && offer.getRoomsCount() > 0) {
            q.must(QueryBuilders.termQuery("roomsCount", offer.getRoomsCount()));
        }

        if (offer.getSquareTotal() != null && offer.getSquareTotal() > 0) {
            q.must(QueryBuilders.rangeQuery("squareTotal").lte(offer.getSquareTotal() + 10).gte(offer.getSquareTotal() - 10));
        }

        /*if (offer.getLocationLat() != null) {
            GeoDistanceQueryBuilder gdr = QueryBuilders.geoDistanceQuery("location");
            gdr.point(offer.getLocationLat(), offer.getLocationLon());
            gdr.distance("500m");
            q.filter(gdr);
        } else if (offer.getDistrict() != null && offer.getDistrict().length() > 0) {
            q.must(QueryBuilders.termQuery("district", offer.getDistrict()));
        }*/

        rb.setQuery(q);

        SearchResponse response = rb.execute().actionGet();

        ListResult r = new ListResult();
        r.hitsCount = response.getHits().getTotalHits();


        for (SearchHit sh: response.getHits()) {
            String dataJson = sh.getSourceAsMap().get(E_DATAFIELD).toString();
            offerList.add(gson.fromJson(dataJson, Offer.class));
        }

        r.list = offerList;

        return r;
    }


    public Offer get (long id) {

        this.logger.info("get");

        Offer result = null;

        GetResponse response = this.elasticClient.prepareGet(E_INDEX, E_TYPE, Long.toString(id)).get();

        String dataJson = response.getSourceAsMap().get(E_DATAFIELD).toString();
        result = gson.fromJson(dataJson, Offer.class);

        return result;
    }


    public Offer save (Offer offer) throws Exception {

        this.logger.info("save");

        Offer result;
        getCoordsDistrict(offer);
        if (offer.getId() != null) {
            Offer so = get(offer.getId());

            assert (so != null);
            offer.setChangeDate(getUnixTimestamp());
            if (offer.equals(so) == false) {
                if (offer.getAgentId() != null && !offer.getAgentId().equals(so.getAgentId())) {
                    offer.setAssignDate(getUnixTimestamp());
                }
                if (offer.getAddressBlock().equals(so.getAddressBlock()) == false) {
                    getCoordsDistrict(offer);
                }
            }
        }
        if (offer.getAgentId() != null){
            offer.setAgent(userService.get(offer.getAgentId()));
        }
        offer.preIndex();
        indexOffer(offer);

        return offer;
    }


    public String delete (String id) {
        DeleteResponse response = this.elasticClient.prepareDelete("rplus", "offer", id).get();
        return id;
    }

    private void getCoordsDistrict(Offer offer) {

        assert (offer.getAddressBlock() != null);

        String addressBlock = offer.getAddressBlock().getAsString();

        if (addressBlock.length() > 0) {
            Double[] latLon = GeoUtils.getCoordsByAddr(addressBlock);
            if (latLon != null) {
                offer.setLocation(new auxclass.GeoPoint(latLon[0], latLon[1]));

                List<String> districts = GeoUtils.getLocationDistrict(latLon[0], latLon[1]);
                if (!districts.isEmpty()) {
                    offer.getAddressBlock().setArea(districts.get(0));
                }
            }
        }
    }

    private void indexOffer(Offer offer) {

        if (offer.getId() == null) {
            offer.setId(CommonUtils.getSystemTimestamp());
        }

        String address = offer.getAddressBlock().getAsString().toLowerCase();

        address = address.replace("й","и");
        address = address.replace("ё","е");
        address = address.replace("ъ","ь");

        ArrayList<String> specArray = new ArrayList<>();
        specArray.add(CommonUtils.strNotNull(dBalcony.get(offer.getBalcony())));
        specArray.add(CommonUtils.strNotNull(dBathroom.get(offer.getBathroom())));
        specArray.add(CommonUtils.strNotNull(dCondition.get(offer.getCondition())));
        specArray.add(CommonUtils.strNotNull(dHouseType.get(offer.getHouseType())));
        specArray.add(CommonUtils.strNotNull(dRoomScheme.get(offer.getRoomScheme())));

        String spec = String.join(" ", specArray);
        Map<String, Object> json = new HashMap<String, Object>();
        json.put("id", offer.getId());
        json.put("accountId", offer.getAccountId());
        json.put("address_ext", CommonUtils.strNotNull(address));
        json.put("spec", spec.toLowerCase());
        json.put("description", CommonUtils.strNotNull(offer.getDescription()).toLowerCase());
        String tags = CommonUtils.strNotNull(address).toLowerCase() + " "
            + CommonUtils.strNotNull(spec).toLowerCase() + " " + CommonUtils.strNotNull(offer.getDescription()).toLowerCase();
        tags = tags.replaceAll("\\.", "\\. ");
        tags = tags.replaceAll("  ", " ");
        json.put("tags", tags);

        // geo search
        if (offer.getLocation() != null) {
            json.put("location", new GeoPoint(offer.getLocation().getAsString()).geohash());
        }
        if (offer.getStateCode() == null)
            offer.setStateCode("raw");
        // filters
        json.put("offerTypeCode", offer.getOfferTypeCode());
        json.put("typeCode", offer.getTypeCode());
        json.put("stateCode", offer.getStateCode());
        json.put("agentId", offer.getAgentId());
        json.put("personId", offer.getPersonId());
        json.put("companyId", offer.getCompanyId());
        json.put("changeDate", offer.getChangeDate());

        // range query
        json.put("floor", offer.getFloor());
        json.put("ownerPrice", offer.getOwnerPrice());
        json.put("roomsCount", offer.getRoomsCount());
        json.put("squareTotal", offer.getSquareTotal());
        json.put("importId", offer.getImportId());
        // sort
        if (offer.getAddressBlock() != null) {
            json.put("locality", CommonUtils.strNotNull(offer.getAddressBlock().getCity()).toLowerCase());
            json.put("address", CommonUtils.strNotNull(offer.getAddressBlock().getStreet()).toLowerCase());
        }
        json.put("poi", offer.getPoi());


        json.put("houseType", dHouseType.get(offer.getHouseType()));
        json.put("roomScheme", dRoomScheme.get(offer.getRoomScheme()));
        json.put("condition", dCondition.get(offer.getCondition()));
        json.put("balcony", dBalcony.get(offer.getBalcony()));
        json.put("bathroom", dBathroom.get(offer.getBathroom()));

        json.put("addDate", offer.getAddDate());
        json.put("changeDate", offer.getChangeDate());
        json.put("lastSeenDate", offer.getLastSeenDate());
        if(offer.getMortgages() == null)
            offer.setMortgages(false);
        if(offer.getNewBuilding() == null)
            offer.setNewBuilding(false);
        json.put("newBuilding", offer.getNewBuilding() ? 1 : 0);
        json.put("mortgages", offer.getMortgages() ? 1 : 0);
        json.put("tag", offer.getTag());
        float locRating =  offer.getLocRating().getMap().get("average");
        float offerRaiting = offer.getOfferRaiting().getMap().get("average");
        json.put("locRating", locRating);
        json.put("offerRaiting", offerRaiting);
        json.put("finalRaiting", (locRating + offerRaiting)/2);
        if (offer.getAgentId() == null) {
            offer.setAgent(null);
            json.put("agentName", null);
        } else {

            User agent = userService.get(offer.getAgentId());

            assert (agent != null);

            offer.setAgent(agent);
            json.put("agentName", agent.getName());
        }

        if (offer.getPersonId() == null) {
            offer.setPerson(null);
            json.put("contactName", null);
            json.put("contactType", null);
            json.put("orgName", null);
            json.put("contactId", null);
        } else {

            Person person = personService.get(offer.getPersonId());

            assert (person != null);

            offer.setPerson(person);
            json.put("contactName", person.getName());
            json.put("contactType", person.getTypeCode());
            json.put("contactId", person.getId());
            if (person.getOrganisationId() == null) {
                json.put("orgName", null);
            } else {

                Organisation org = person.getOrganisation();

                assert (org != null);
                json.put("organisationId", org.getId());
                json.put("contactType", org.getTypeCode());
                json.put("orgName", org.getName());

            }
        }

        offer.preIndex();
        json.put(E_DATAFIELD, gson.toJson(offer));
        IndexResponse response = this.elasticClient.prepareIndex(E_INDEX, E_TYPE, Long.toString(offer.getId())).setSource(json).get();
    }

    public void updateOffers(String script_text, Map<String, String> filter){
        UpdateByQueryRequestBuilder ubqrb = UpdateByQueryAction.INSTANCE.newRequestBuilder(this.elasticClient);

        Script script = new Script(script_text);
        BoolQueryBuilder q = QueryBuilders.boolQuery();
        filter.forEach((k,v) -> {
            q.must(QueryBuilders.termQuery(k, v));
        });
        ubqrb.source(E_INDEX).source().setTypes(E_TYPE).script(script, Collections.emptyMap()).filter(q);
        BulkByScrollResponse response = ubqrb.get();
    }
}
