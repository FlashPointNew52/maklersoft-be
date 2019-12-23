package service;

import auxclass.PhoneBlock;
import com.google.gson.Gson;
import entity.Comment;
import entity.Rating;
import entity.Person;
import entity.User;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.DeleteByQueryAction;
import org.elasticsearch.index.reindex.DeleteByQueryRequestBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.CommonUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RatingService {
    private final String E_INDEX = "ms_rate";
    private final String E_TYPE = "rating";
    private final String E_DATAFIELD = "data";

    Logger logger = LoggerFactory.getLogger(RatingService.class);
    private final Client elasticClient;
    private PersonService personService;
    private UserService userService;

    Gson gson = new Gson();

    public RatingService(Client elasticClient) {
        this.elasticClient = elasticClient;
        this.personService = null;
        this.userService = null;
    }

    public void setPersonService(PersonService personService){
        this.personService = personService;
    }
    public void setUserService(UserService userService){
        this.userService = userService;
    }

    public float getAverage (List<String> phones, String type) {

        SearchRequestBuilder rb = elasticClient.prepareSearch(E_INDEX)
                .setTypes(E_TYPE).setSize(10000);

        BoolQueryBuilder q = QueryBuilders.boolQuery();

        if(phones.size() > 0){
            BoolQueryBuilder ph = QueryBuilders.boolQuery();
            for (String phone: phones ) {
                ph.should(QueryBuilders.termQuery("phones", phone));
            }
            if(type.equals("organisation")){
                q.must(QueryBuilders.termQuery("objType", "organisation"));
            } else{
                q.mustNot(QueryBuilders.termQuery("objType", "organisation"));
            }
            q.must(ph);
            rb.setQuery(q);
            SearchResponse response = rb.execute().actionGet();
            float everage = 0;
            float count = 0;
            for (SearchHit sh: response.getHits()) {

                String dataJson = sh.getSourceAsMap().get(E_DATAFIELD).toString();

                Rating rate = gson.fromJson(dataJson, Rating.class);
                if(rate.getAvarege_mark() > 0){
                    everage += rate.getAvarege_mark();
                    count ++;
                }
            }
            if(count == 0) count++;
            return everage/count;
        } else{
            return 0;
        }
    }

    public Rating get (Long objId, Long userId, String type){
        Rating rate = new Rating();
        SearchRequestBuilder rb = elasticClient.prepareSearch(E_INDEX)
                .setTypes(E_TYPE).setSize(1);

        BoolQueryBuilder q = QueryBuilders.boolQuery();
        q.must(QueryBuilders.termQuery("objType", type));
        q.must(QueryBuilders.termQuery("agentId", userId));
        q.must(QueryBuilders.termQuery("objId", objId));
        rb.setQuery(q);
        SearchResponse response = rb.execute().actionGet();

        for (SearchHit sh: response.getHits()) {
            String dataJson = sh.getSourceAsMap().get(E_DATAFIELD).toString();
            rate = gson.fromJson(dataJson, Rating.class);
        }

        return rate;
    }

    public Rating save (Rating rating) throws Exception {
        index(rating);
        return rating;
    }

    private void index(Rating rating) {
        this.logger.info("index");
        Map<String, Object> json = new HashMap<String, Object>();

        if(rating.getId() == null) {
            rating.setId(CommonUtils.getSystemTimestamp());
        }

        float evarage = 0;
        int count = 0;
        if(rating.getMark1() > 0){
            evarage += rating.getMark1();
            count++;
        }
        if(rating.getMark2() > 0){
            evarage += rating.getMark2();
            count++;
        }
        if(rating.getMark3() > 0){
            evarage += rating.getMark3();
            count++;
        }
        if(rating.getMark4() > 0){
            evarage += rating.getMark4();
            count++;
        }
        if(rating.getMark5() > 0){
            evarage += rating.getMark5();
            count++;
        }
        if(count == 0)  count++;

        rating.setAvarege_mark(evarage/count);

        json.put("id", rating.getId());
        json.put("agentId", rating.getAgentId());
        json.put("objId", rating.getObjId());
        json.put("objType", rating.getObjType());
        json.put("phones", rating.getPhones());
        json.put(E_DATAFIELD, gson.toJson(rating));

        IndexResponse response = this.elasticClient.prepareIndex(E_INDEX, E_TYPE, Long.toString(rating.getId())).setSource(json).get();
    }

    private Rating get_only (int objType, Long objId, Long userId) {

        Rating rating = new Rating();

        SearchRequestBuilder rb = elasticClient.prepareSearch(E_INDEX)
                .setTypes(E_TYPE).setSize(1);

        BoolQueryBuilder q = QueryBuilders.boolQuery();

        q.must(QueryBuilders.termQuery("agentId", userId));
        q.must(QueryBuilders.termQuery("objId", objId));
        q.must(QueryBuilders.termQuery("objType", objType));

        rb.setQuery(q);
        SearchResponse response = rb.execute().actionGet();

        for (SearchHit sh: response.getHits()) {
            String dataJson = sh.getSourceAsMap().get(E_DATAFIELD).toString();
            rating = gson.fromJson(dataJson, Rating.class);
        }

        return rating;
    }

    public void refresh(){
        elasticClient.admin().indices()
                .prepareRefresh(E_INDEX)
                .get();
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
}
