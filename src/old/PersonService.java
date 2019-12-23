package service;

import com.google.gson.Gson;
import entity.Rating;
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
import auxclass.PhoneBlock;
import entity.Person;
import entity.History;
import static utils.CommonUtils.getUnixTimestamp;

public class PersonService {

    private final String E_INDEX = "ms_main";
    private final String E_TYPE = "person";
    private final String E_DATAFIELD = "data";

    Logger logger = LoggerFactory.getLogger(PersonService.class);
    private final Client elasticClient;
    private final HistoryService historyService;
    private final RatingService ratingService;
    Gson gson = new Gson();

    public PersonService (Client elasticClient,
                    HistoryService historyService,
                    RatingService ratingService
    ) {

        this.elasticClient = elasticClient;
        this.historyService = historyService;
        this.ratingService = ratingService;
    }

    public List<ErrorObject> check (Person person) {
        List<ErrorObject> errors = new LinkedList<>();
        try{
            if(person.getAccountId() == null){
                errors.add(new ErrorObject("001", "Account not specified"));
            }
            if((person.getEmailBlock() == null && person.getPhoneBlock() == null) || person.getEmailBlock().getAsList().size() + person.getPhoneBlock().getAsList().size() == 0 ){
                errors.add(new ErrorObject("200", "No phones or emails"));
            } else if(person.getId() == null){
                SearchRequestBuilder rb = elasticClient.prepareSearch(E_INDEX)
                        .setTypes(E_TYPE)
                        .setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
                        .setFrom(0).setSize(1);

                BoolQueryBuilder q = QueryBuilders.boolQuery();
                BoolQueryBuilder qw = QueryBuilders.boolQuery().minimumShouldMatch(1);
                for (String mail: person.getEmailBlock().getAsList()) {
                    qw.should(QueryBuilders.matchQuery("emails", mail.replaceAll("@", "")));
                }
                for (String phone: person.getPhoneBlock().getAsList()) {
                    qw.should(QueryBuilders.matchQuery("phones", phone));
                }
                q.must(qw);
                q.must(QueryBuilders.termQuery("accountId", person.getAccountId()));
                rb.setQuery(q);

                SearchResponse response = rb.execute().actionGet();

                for (SearchHit sh: response.getHits()) {
                    String dataJson = sh.getSourceAsMap().get(E_DATAFIELD).toString();
                     errors.add(new ErrorObject("300", "Сontact with such email or phone already exists", dataJson));
                }
            }
        } catch(Exception exp){
            errors.add(new ErrorObject("900","System error", exp.toString()));
            this.logger.info("error: " + exp.toString());
        }
        return errors;
    }

    public Person getByPhone (List<String> phones, Long accountId) {

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

        if(accountId != null){
            q.must(QueryBuilders.termQuery("accountId", accountId));
        }
        rb.setQuery(q);
        SearchResponse response = rb.execute().actionGet();

        Person pers = null;
        for (SearchHit sh: response.getHits()) {
            String dataJson = sh.getSourceAsMap().get(E_DATAFIELD).toString();
            pers = gson.fromJson(dataJson, Person.class);
            break;
        }

        return pers;
    }

    public List<Person> list (Long accountId, Long userId, int page, int perPage,  Map<String, String> filter, Map<String, String> sort, String searchQuery) {

        List<Person> personList = new ArrayList<>();

        SearchRequestBuilder rb = elasticClient.prepareSearch(E_INDEX)
                .setTypes(E_TYPE)
                .setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
                .setFrom(page * perPage).setSize(perPage);

        BoolQueryBuilder q = QueryBuilders.boolQuery();

        ParseResult pr = Query.parseContacts(searchQuery);
        List<FilterObject> rangeFilters = pr.filterList;

        q.must(QueryBuilders.termQuery("accountId", accountId));

        /*if (userId != null) {
            q.must(QueryBuilders.termQuery("agentId", userId));
        }

        if (typeCode != null) {
            q.must(QueryBuilders.termQuery("typeCode", typeCode));
        }*/


        if (pr.query != null && pr.query.length() > 2) {
            q.must(QueryBuilders.matchQuery("tags", pr.query).operator(Operator.AND));
            q.should(QueryBuilders.matchQuery("name", pr.query).boost(8));
            q.should(QueryBuilders.matchQuery("organisation", pr.query).boost(4));
        }

        filter.forEach((k,v) -> {
            if (v != null && !v.equals("all")) {
                if (k.equals("typeCode")) {
                    if (v.equals("realtor")) {
                        q.must(QueryBuilders.termQuery("typeCode", "realtor"));
                    } else if (v.equals("partner")) {
                        q.must(QueryBuilders.termQuery("typeCode", "partner"));
                    } else if (v.equals("owner")) {
                        q.must(QueryBuilders.termQuery("typeCode", "owner"));
                    } else if (v.equals("client")) {
                        q.must(QueryBuilders.termQuery("typeCode", "client"));
                    } else if (v.contains("my")) {
                        q.must(QueryBuilders.termQuery("agentId", userId));
                    }
                } else if (k.equals("changeDate") || k.equals("addDate")) {
                    long date = Long.parseLong(v);
                    // 86400 sec in 1 day
                    long ts = CommonUtils.getUnixTimestamp() - date * 86400;
                    q.must(QueryBuilders.rangeQuery(k).gte(ts));
                } else if (k.equals("agentId")) {
                    if(v.charAt(0) == '['){
                        String ids[] = v.substring(1, v.length() - 1).split(",");

                        BoolQueryBuilder bq = QueryBuilders.boolQuery();
                        for(String id: ids) {
                            bq.should(QueryBuilders.termQuery("agentId", Long.parseLong(id)));
                        }
                        q.must(bq);
                    } else{
                        q.must(QueryBuilders.termQuery("agentId", v));
                    }
                } else {
                    if(!v.equals("exist"))
                        q.must(QueryBuilders.termQuery(k, v));
                    else q.filter(QueryBuilders.existsQuery(k));
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

        rb.setQuery(q);

        SearchResponse response = rb.execute().actionGet();

        for (SearchHit sh: response.getHits()) {
            String dataJson = sh.getSourceAsMap().get(E_DATAFIELD).toString();
            personList.add(gson.fromJson(dataJson, Person.class));
        }
        return personList;
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
            Person pers = gson.fromJson(dataJson, Person.class);
            pers.setRate(ratingService.getAverage(pers.getPhoneBlock().getAsList(), "person"));
            try {
                pers = save(pers);
            } catch(Exception exp){}
        }
    }

    public Person get (Long id) {

        Person result = null;
        try {
            GetResponse response = this.elasticClient.prepareGet(E_INDEX, E_TYPE, Long.toString(id)).get();
            String dataJson = response.getSourceAsMap().get(E_DATAFIELD).toString();
            result = gson.fromJson(dataJson, Person.class);
        } catch (Exception exp){}

        return result;
    }

    public Person save (Person person) throws Exception {
        person.setRate(ratingService.getAverage(person.getPhoneBlock().getAsList(), "person"));

        this.logger.info("save");
        /*if(person.getId() != null){
            Person oldPerson = get(person.getId());
            if(oldPerson != null && !person.equals(oldPerson)){
                History hist_rec = new History(person.getId(), CommonUtils.getSystemTimestamp(), oldPerson);
                logger.info("hist_rec " + hist_rec.getId());
                this.historyService.save(hist_rec);
                logger.info("not Same");
            } else
                logger.info("Same");
        }*/

        indexPerson(person);

        return person;
    }

    public Person delete (long id) {
        return null;
    }


    public void indexPerson(Person person) {

        Map<String, Object> json = new HashMap<String, Object>();

        if (person.getId() == null) {
            person.setId(CommonUtils.getSystemTimestamp());
            person.setAddDate(CommonUtils.getUnixTimestamp());
        }

        person.setChangeDate(CommonUtils.getUnixTimestamp());

        json.put("id", person.getId());
        json.put("accountId", person.getAccountId());
        String tags = CommonUtils.strNotNull(person.getName()).toLowerCase();
        json.put("name", tags);
        json.put("changeDate", person.getChangeDate());
        json.put("addDate", person.getAddDate());
        json.put("assignDate", person.getAssignDate());
        json.put("userRef", person.getUserRef());

        if(person.getTypeCode() == null)
            person.setTypeCode("client");
        if(person.getStateCode() == null)
            person.setStateCode("raw");
        /*if(person.getRate() == null)
            person.setRate(0);*/
        json.put("typeCode", person.getTypeCode());
        json.put("stateCode", person.getStateCode());
        json.put("rate", person.getRate());
        // filters
        json.put("agentId", person.getAgentId());
        String agent_name = "";
        if(person.getAgent() != null){
            agent_name = CommonUtils.strNotNull(person.getAgent().getName()).toLowerCase().replaceAll("\"", "");
            json.put("agent", agent_name);
        }

        json.put("organisationId", person.getOrganisationId());
        String org_name = "";
        if(person.getOrganisation() != null){
            org_name = CommonUtils.strNotNull(person.getOrganisation().getName()).toLowerCase().replaceAll("\"", "");
            json.put("organisation", org_name);
        }

        tags += " " + agent_name + " " + org_name + " ";
        json.put("tags", tags);
        List<String> phoneArray = person.getPhoneBlock().getAsList();
        json.put("phones", String.join(" ", phoneArray));

        List<String> mailArray = person.getEmailBlock().getAsList();
        json.put("emails", String.join(" ", mailArray).replace("@",""));

        json.put("tag", person.getTag());

        json.put(E_DATAFIELD, gson.toJson(person));

        IndexResponse response = this.elasticClient.prepareIndex(E_INDEX, E_TYPE, Long.toString(person.getId())).setSource(json).get();
    }
}
