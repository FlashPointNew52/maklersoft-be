package service;

import auxclass.ErrorMsg;
import auxclass.UploadFile;
import entity.*;
import com.google.gson.*;

import configuration.AppConfig;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.geo.GeoPoint;
import org.elasticsearch.index.query.*;
import org.elasticsearch.index.reindex.*;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.*;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
    }

    public ListResult list (Long accountId, Long userId, int page, int perPage, Map<String, Object> filter, Map<String, String> sort, String searchQuery, List<GeoPoint> geoSearchPolygon) {
        ListResult r = new ListResult();

        List<Offer> offerList = new ArrayList<>();
        Map<String, String> queryParts = Query.process(searchQuery.toLowerCase());
        String request = queryParts.get("req");

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

        filter.forEach((k,v) -> {
            if(k.equals("attachment") && v != null){
                if (v.equals("all")) {
                    BoolQueryBuilder qw = QueryBuilders.boolQuery().minimumShouldMatch(1);
                    qw.should(QueryBuilders.termQuery("accountId", accountId));
                    qw.should(QueryBuilders.termQuery("stageCode", "listing"));
                    q.must(qw);
                } else {
                    q.must(QueryBuilders.termQuery("accountId", accountId));
                    if(v.equals("my"))
                        q.must(QueryBuilders.termQuery("agentId", userId));
                }
            } else if(v != null && !v.equals("all")) {
                if(k.equals("changeDate") || k.equals("addDate")) {
                    long date = Long.parseLong(v.toString());
                    long ts = CommonUtils.getUnixTimestamp() - date * 86400; // 86400 sec in 1 day
                    q.must(QueryBuilders.rangeQuery(k).gte(ts));
                } else if(k.equals("contactType") || k.equals("tag") || k.equals("isMiddleman")) {
                    q.must(QueryBuilders.termQuery("accountId", accountId));
                    q.must(QueryBuilders.termQuery(k, k.equals("tag") ? String.format("%.0f",v) : v));
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
            q.should(QueryBuilders.matchQuery("address", pr.query).boost(10));
            q.should(QueryBuilders.matchQuery("contactName", pr.query).boost(8));
            q.should(QueryBuilders.matchQuery("orgName", pr.query).boost(6));
            q.should(QueryBuilders.matchQuery("agentName", pr.query).boost(4));
            q.should(QueryBuilders.matchQuery("description", pr.query));
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
//        this.logger.info(q.toString());
        rb.setQuery(q);

        SearchResponse response = rb.execute().actionGet();

        r.hitsCount = response.getHits().getTotalHits();

        for (SearchHit sh: response.getHits()) {
            String dataJson = sh.getSourceAsMap().get(E_DATAFIELD).toString();
            Offer ofr = gson.fromJson(dataJson, Offer.class);

            if(ofr.getPersonId() != null){
                ofr.setPerson(personService.get(ofr.getPersonId()));
            } else if(ofr.getCompanyId() != null){
                ofr.setCompany(organisationService.get(ofr.getCompanyId()));
            }
            if(ofr.getAgentId() != null){
                ofr.setAgent(userService.get(ofr.getAgentId()));
            }
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
                if(ofr.getPerson() != null){
                    try{
                        ofr.setPhoneBlock(ofr.getPerson().getPhoneBlock());
                        ofr.setEmailBlock(ofr.getPerson().getEmailBlock());
                    } catch (NullPointerException npe){ }
                } else if(ofr.getCompany() != null){
                    if(ofr.getCompany().getPhoneBlock() != null)
                        ofr.setPhoneBlock(ofr.getCompany().getPhoneBlock());
                    if(ofr.getCompany().getEmailBlock() != null)
                        ofr.setEmailBlock(ofr.getCompany().getEmailBlock());
                }
            }
            offerList.add(ofr);
        }

        r.list = offerList;

        return r;
    }


    public ListResult listImport (Long accountId, Long userId, int page, int perPage, Map<String, Object> filter, Map<String, String> sort, String searchQuery, List<GeoPoint> geoSearchPolygon)
    throws UnsupportedEncodingException{
        Map<String, String> queryParts = Query.process(searchQuery.toLowerCase());
        String request = queryParts.get("req");

        ParseResult pr = Query.parse(request);
        List<FilterObject> rangeFilters = pr.filterList;

        List<Offer> offerList = new ArrayList<>();
        Long hitsCount = 0L;
        ListResult r = new ListResult();
        r.hitsCount = 0;


        String attachment = filter.get("attachment").toString();
        attachment = attachment != null ? attachment : "all";
        String isMiddleman = filter.get("isMiddleman").toString();
        filter.remove("attachment");
        filter.remove("isMiddleman");
        filter.remove("tag");
        filter.remove("stageCode");
        filter.remove("contactType");
        List<Person> competCliList = new ArrayList<>();
        List<String> phones = new ArrayList<>();

        if (attachment.contains("my")) {
            phones = userService.get(userId).getPhoneBlock().getAsList();
        } else if(attachment.contains("our")){
            List<User> userList = userService.list(accountId, null, 0, 1000, new HashMap<String, Object>() {{put("typeCode", "company");}}, null, "");
            for(User usr : userList){
                phones.addAll(0, usr.getPhoneBlock().getAsList());
            }

            List<Organisation> orgList = organisationService.list(accountId, null, 0, 1000, new HashMap<String, Object>() {{put("ourCompany", 1);}}, null, "");
            for (Organisation org : orgList) {
                phones.addAll(0, org.getPhoneBlock().getAsList());
            }
        }
        if(phones.size() > 0)
            rangeFilters.add(new FilterObject("phones", phones));

        phones = new ArrayList<>();
        if(isMiddleman != null && (isMiddleman.contains("owner") || isMiddleman.contains("middleman"))){
            List<Person> clientList = personService.list(accountId, null, 0, 1000,
                    new HashMap<String, Object>() {{put("isMiddleman", isMiddleman.equals("middleman") ? "owner" : "middleman");}}, null, "");
            for (Person psn : clientList) {
                phones.addAll(0, psn.getPhoneBlock().getAsList());
            }
            List<Organisation> orgList = organisationService.list(accountId, null, 0, 1000, new HashMap<String, Object>() {{put("isMiddleman", isMiddleman.equals("middleman") ? "owner" : "middleman");}}, null, "");
            for (Organisation org : orgList) {
                phones.addAll(0, org.getPhoneBlock().getAsList());
            }
            if(phones.size() == 0)
                phones.add("0");
            FilterObject flt = new FilterObject("mediatorCompany", phones);
            if (isMiddleman.contains("owner"))
                flt.exactVal = 0;
            else
                flt.exactVal = 1;
            rangeFilters.add(flt);
        }


        String urlParameters = "{\"query\":" + "\"" +pr.query + "\""
            + ",\"rangeFilters\":" + gson.toJson(rangeFilters)
            + ",\"filter\":" + gson.toJson(filter)
            + ",\"page\":" + page
            + ",\"per_page\":" + perPage
            + ",\"sort\":" + gson.toJson(sort)
            + ",\"search_area\":" + (geoSearchPolygon.size() > 0 ? gson.toJson(geoSearchPolygon) : "\"\"") + "}";
        byte[] postData       = urlParameters.getBytes( StandardCharsets.UTF_8 );
        int    postDataLength = postData.length;

        try {
            URL iurl = new URL(AppConfig.IMPORT_URL + "/api/offers/");
            HttpURLConnection uc = (HttpURLConnection) iurl.openConnection();
            uc.setDoOutput( true );
            uc.setDoInput( true );
            uc.setRequestProperty( "Content-Type", "application/json; charset=UTF-8");
            uc.setRequestMethod( "POST" );

            uc.setRequestProperty( "charset", "utf-8");
            uc.setRequestProperty( "Content-Length", Integer.toString( postDataLength ));

            uc.connect();
            try (OutputStream os = uc.getOutputStream()) {
                os.write(postData);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
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
                    hitsCount = jsonObject.get("total").getAsJsonObject().get("value").getAsLong();
                    if(hitsCount > 0){
                        JsonArray t = jsonObject.get("hits").getAsJsonArray();
                        for(int i=0; i<t.size(); ++i){

                            String os = t.get(i).getAsJsonObject().get("_source").toString();
                            try {
                                Offer offer = gson.fromJson(os, Offer.class);
                                offer.setLocRating(new auxclass.Rating());
                                offer.setLastSeenDate(0L);
                                Map<String, Object> flt = new HashMap<String, Object>(){{put("importId", ""+offer.getImportId()); put("attachment", "our");}};
                                List<Offer> coof = list(accountId, null, 0, 1, flt, null, "", Arrays.asList(new GeoPoint[0])).list;
                                if(coof.size() > 0){
                                    offer.setOfferRef(coof.get(0).getId());
                                }
                                offer.setPerson(null);
                                offer.setCompany(null);

                                Person client = personService.getByPhone(offer.getPhoneBlock().getAsList(), accountId);

                                if(client != null){
                                    offer.setPerson(client);
                                    offer.setPersonId(client.getId());
                                } else {
                                    Organisation org = organisationService.getByPhone(offer.getPhoneBlock().getAsList(), accountId);
                                    if(org != null){
                                        offer.setCompany(org);
                                        offer.setCompanyId(org.getId());
                                    } else{
                                        User user = userService.getByPhone(offer.getPhoneBlock().getAsList());
                                        if(user != null) {
                                            offer.setAgent(user);
                                            offer.setAgentId(user.getId());
                                        }
                                    }
                                }
                                offerList.add(offer);
                            } catch (Exception ex) {
                                ex.printStackTrace();
                                logger.error(ex.getMessage() + "\n" + os);
                            }
                        }
                    }
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

        if (offer.getRoomsCount() != null && offer.getRoomsCount() > 0) {
            q.must(QueryBuilders.termQuery("roomsCount", offer.getRoomsCount()));
        }

        if (offer.getSquareTotal() != null && offer.getSquareTotal() > 0) {
            q.must(QueryBuilders.rangeQuery("squareTotal").lte(offer.getSquareTotal() + 10).gte(offer.getSquareTotal() - 10));
        }

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
        Offer result;
        GetResponse response = this.elasticClient.prepareGet(E_INDEX, E_TYPE, Long.toString(id)).get();
        String dataJson = response.getSourceAsMap().get(E_DATAFIELD).toString();
        result = gson.fromJson(dataJson, Offer.class);
        return result;
    }


    public Offer save (Offer offer) throws Exception {
        Offer result;
        //getCoordsDistrict(offer);
        if (offer.getId() != null) {
            Offer so = get(offer.getId());

            assert(so != null);
            offer.setChangeDate(getUnixTimestamp());
            if(!offer.equals(so)) {
                if(offer.getAgentId() != null && !offer.getAgentId().equals(so.getAgentId())) {
                    offer.setAssignDate(getUnixTimestamp());
                }
                /*if(!offer.getAddressBlock().equals(so.getAddressBlock())) {
                    if(offer.getAddressBlock().getAdmArea() == null && offer.getAddressBlock().getArea() == null)
                        getCoordsDistrict(offer);
                }*/
            }
        }
        if (offer.getAgentId() != null){
            offer.setAgent(userService.get(offer.getAgentId()));
        }
        indexOffer(offer);

        return offer;
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
        BulkByScrollResponse response =
                new DeleteByQueryRequestBuilder(elasticClient, DeleteByQueryAction.INSTANCE)
                        .filter(q)
                        .source(E_INDEX)
                        .get();
        long deleted = response.getDeleted();
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

        logger.info("for deleting: " + response.getHits().getTotalHits() + " offers");
        Long i = 0l;
        for (SearchHit sh: response.getHits()) {
            String dataJson = sh.getSourceAsMap().get(E_DATAFIELD).toString();
            Offer ofr = gson.fromJson(dataJson, Offer.class);
            try{
                delete(ofr.getId().toString());
                UploadFile.deleteDirectory(AppConfig.FILE_STORAGE_PATH + "photo/offers/"  + ofr.getAccountId() + "/" + ofr.getId());
                UploadFile.deleteDirectory(AppConfig.FILE_STORAGE_PATH + "docs/offers/"  + ofr.getAccountId() + "/" + ofr.getId());
                i++;
            } catch (Exception exp){
                continue;
            }
        }
        return i;
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
                    if (districts.size() > 1){
                        offer.getAddressBlock().setAdmArea(districts.get(1));
                    }
                }
            }
        }
    }

    private void indexOffer(Offer offer) {
        offer.preIndex();

        String address = CommonUtils.replaceSymb(offer.getAddressBlock().getAsString());
        String agentName = null;
        String contactName = null;
        String contactType = null;
        Boolean isMiddleman = null;
        String orgName = null;
        User agent = null;
        Person person = null;
        Organisation org = null;
        Long orgId = null;
        if (offer.getAgentId() != null) {
            agent = userService.get(offer.getAgentId());
            agentName = agent.getName();
        }

        if (offer.getPersonId() != null) {
            person = personService.get(offer.getPersonId());
            contactName = person.getName();
            contactType = person.getTypeCode();
            isMiddleman = person.getIsMiddleman() || false;

            offer.setPhoneBlock(person.getPhoneBlock());
            offer.setEmailBlock(person.getEmailBlock());

            if (person.getOrganisationId() != null) {
                person.setOrganisation(organisationService.get(person.getOrganisationId()));
                orgName = person.getOrganisation().getName();
                orgId = person.getOrganisationId();
            }
        } else if(offer.getCompanyId() != null){
            org = organisationService.get(offer.getCompanyId());

            contactName = org.getName();
            contactType = org.getTypeCode();
            isMiddleman = org.getIsMiddleman() || false;

            offer.setPhoneBlock(org.getPhoneBlock());
            offer.setEmailBlock(org.getEmailBlock());
        }

        Map<String, Object> json = new HashMap<String, Object>();

        json.put("id", offer.getId());
        json.put("accountId", offer.getAccountId());

        json.put("address", address);
        json.put("description", CommonUtils.replaceSymb(offer.getDescription()));
        json.put("agentName", CommonUtils.replaceSymb(agentName));
        json.put("contactName", CommonUtils.replaceSymb(contactName));
        json.put("orgName", CommonUtils.replaceSymb(orgName));
        json.put("tags", json.get("address") + " " + json.get("contactName") + " " + json.get("orgName") + " " + json.get("agentName") + " " + json.get("description"));

        if(offer.getOfferTypeCode().equals("rent")){
            json.put("complete", offer.getConditions().isComplete());
            json.put("living_room_furniture", offer.getConditions().isLiving_room_furniture() );
            json.put("kitchen_furniture", offer.getConditions().isKitchen_furniture());
            json.put("couchette", offer.getConditions().isCouchette());
            json.put("bedding", offer.getConditions().isBedding());
            json.put("dishes", offer.getConditions().isDishes());
            json.put("refrigerator", offer.getConditions().isRefrigerator());
            json.put("washer", offer.getConditions().isWasher());
            json.put("microwave_oven", offer.getConditions().isMicrowave_oven());
            json.put("air_conditioning", offer.getConditions().isAir_conditioning());
            json.put("dishwasher", offer.getConditions().isDishwasher());
            json.put("tv", offer.getConditions().isTv());

            json.put("with_animals", offer.getConditions().isWith_animals());
            json.put("with_children", offer.getConditions().isWith_children());

            json.put("prepayment", offer.getPrepayment());
        }

        json.put("phones", String.join(" ", offer.getPhoneBlock().getAsList()));
        json.put("emails", String.join(" ", offer.getEmailBlock().getAsList()).replace("@",""));

        json.put("isMiddleman", isMiddleman ? "middleman" : "owner");
        json.put("contactType", CommonUtils.replaceSymb(contactType));
        // filters
        json.put("offerTypeCode", offer.getOfferTypeCode());
        json.put("typeCode", offer.getTypeCode());
        json.put("buildingClass", offer.getBuildingClass());
        json.put("stageCode", offer.getStageCode());
        json.put("agentId", offer.getAgentId());
        json.put("personId", offer.getPersonId());
        json.put("companyId", offer.getCompanyId());
        json.put("orgId", orgId);
        json.put("houseType", offer.getHouseType());
        json.put("roomScheme", offer.getRoomScheme());
        json.put("condition", offer.getCondition());
        json.put("balcony", offer.getBalcony());
        json.put("bathroom", offer.getBathroom());

        json.put("addDate", offer.getAddDate());
        json.put("changeDate", offer.getChangeDate());
        json.put("lastSeenDate", offer.getLastSeenDate());

        json.put("floor", offer.getFloor());
        json.put("ownerPrice", offer.getOwnerPrice());
        json.put("roomsCount", offer.getRoomsCount());
        json.put("squareTotal", offer.getSquareTotal());
        json.put("importId", offer.getImportId());
        json.put("locality", CommonUtils.replaceSymb(offer.getAddressBlock().getCity()));
        json.put("street", CommonUtils.replaceSymb(offer.getAddressBlock().getStreet()));
        json.put("poi", offer.getPoi());
        json.put("newBuilding", offer.getNewBuilding());
        json.put("mortgage", offer.getMortgages());
        json.put("tag", offer.getTag());

        if (offer.getLocation() != null) {
            json.put("location", new GeoPoint(offer.getLocation().getAsString()).geohash());
        }

        json.put("locRating", offer.getLocRating().getMap().get("average"));

        offer.setPerson(null);
        offer.setAgent(null);
        offer.setCompany(null);

        json.put(E_DATAFIELD, gson.toJson(offer));
        IndexResponse response = this.elasticClient.prepareIndex(E_INDEX, E_TYPE, Long.toString(offer.getId())).setSource(json).get();
        offer.setPerson(person);
        offer.setAgent(agent);
        offer.setCompany(org);
    }

    public void updateOffers(String script_text, Map<String, Object> filter) {
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
