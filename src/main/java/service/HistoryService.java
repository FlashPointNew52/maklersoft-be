package service;

import com.google.gson.Gson;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import entity.History;
import utils.CommonUtils;


public class HistoryService {

    private final String E_INDEX = "rplus";
    private final String E_TYPE = "history";
    private final String E_DATAFIELD = "data";

    Logger logger = LoggerFactory.getLogger(HistoryService.class);
    private final Client elasticClient;
    Gson gson = new Gson();

    public HistoryService (Client elasticClient) {
        this.elasticClient = elasticClient;
    }

    public List<History> list (int page, int perPage, long id, String type, Long date) {

        List<History> historyList = new ArrayList<>();

        SearchRequestBuilder rb = elasticClient.prepareSearch(E_INDEX)
                .setTypes(E_TYPE)
                .setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
                .setFrom(page * perPage).setSize(perPage);
        rb.addSort(SortBuilders.fieldSort("addDate").order(SortOrder.DESC));
        BoolQueryBuilder q = QueryBuilders.boolQuery();

        q.must(QueryBuilders.termQuery("objectId", id));
        q.must(QueryBuilders.termQuery("typeClass", type));

        if(date != null){
            q.must(QueryBuilders.rangeQuery("addDate").lte(date));
        }

        rb.setQuery(q);

        SearchResponse response = rb.execute().actionGet();
        if(response.getHits().totalHits() == 0 && date != null){
            q = QueryBuilders.boolQuery();

            q.must(QueryBuilders.termQuery("objectId", id));
            q.must(QueryBuilders.termQuery("typeClass", type));
            q.must(QueryBuilders.rangeQuery("addDate").gte(date));
            rb = elasticClient.prepareSearch(E_INDEX)
                    .setTypes(E_TYPE)
                    .setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
                    .setFrom(page * perPage).setSize(perPage);
            rb.addSort(SortBuilders.fieldSort("addDate").order(SortOrder.ASC));
            rb.setQuery(q);
            response = rb.execute().actionGet();
        }
        for (SearchHit sh: response.getHits()) {
            String dataJson = sh.getSourceAsMap().get(E_DATAFIELD).toString();
            historyList.add(gson.fromJson(dataJson, History.class));
        }
        return historyList;
    }

    public History get (long id) {

        History result = null;

        GetResponse response = this.elasticClient.prepareGet(E_INDEX, E_TYPE, Long.toString(id)).get();
        String dataJson = response.getSourceAsMap().get(E_DATAFIELD).toString();
        result = gson.fromJson(dataJson, History.class);

        return result;
    }

    public History save (History history) throws Exception {

        this.logger.info("save");

        indexHistory(history);

        return history;
    }

    public History delete (long id) {
        return null;
    }


    public void indexHistory(History history) {

        Map<String, Object> json = new HashMap<String, Object>();

        if (history.getId() == null) {
            history.setId(CommonUtils.getSystemTimestamp());
        }

        json.put("id", history.getId());
        json.put("objectId", history.getObjectId());
        json.put("typeClass", history.getTypeClass());
        json.put("addDate", history.getAddDate());

        json.put(E_DATAFIELD, gson.toJson(history));

        IndexResponse response = this.elasticClient.prepareIndex(E_INDEX, E_TYPE, Long.toString(history.getId())).setSource(json).get();
    }
}
