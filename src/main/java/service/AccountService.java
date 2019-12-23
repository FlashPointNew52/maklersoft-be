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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import entity.Account;
import java.util.*;

import utils.CommonUtils;
import static utils.CommonUtils.getUnixTimestamp;

public class AccountService {

    private final String E_INDEX = "maklersoft_account";
    private final String E_TYPE = "account";
    private final String E_DATAFIELD = "data";

    Logger logger = LoggerFactory.getLogger(UserService.class);
    private final Client elasticClient;
    Gson gson = new Gson();

    public AccountService (Client elasticClient) {

        this.elasticClient = elasticClient;
    }

    public List<Account> list (int page, int perPage, String searchQuery) {

        this.logger.info("list");

        List<Account> accountList = new ArrayList<>();


        SearchRequestBuilder rb = elasticClient.prepareSearch(E_INDEX)
                .setTypes(E_TYPE)
                .setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
                .setFrom(page * perPage).setSize(perPage);


        BoolQueryBuilder q = QueryBuilders.boolQuery();


        if (searchQuery != null && searchQuery.length() > 0) {

            q.should(QueryBuilders.matchQuery("name", searchQuery));
            q.should(QueryBuilders.matchQuery("location", searchQuery));
        }

        rb.setQuery(q);

        SearchResponse response = rb.execute().actionGet();

        for (SearchHit sh: response.getHits()) {
            String dataJson = sh.getSourceAsMap().get(E_DATAFIELD).toString();
            accountList.add(gson.fromJson(dataJson, Account.class));
        }

        return accountList;
    }

    public Account get (long id) {
        this.logger.info("get");

        Account result = null;

        GetResponse response = this.elasticClient.prepareGet(E_INDEX, E_TYPE, Long.toString(id)).get();

        String dataJson = response.getSourceAsMap().get(E_DATAFIELD).toString();
        result = gson.fromJson(dataJson, Account.class);

        return result;
    }

    public Account getByName(String name) {
        this.logger.info("get by name");

        Account result = null;


        SearchRequestBuilder rb = elasticClient.prepareSearch(E_INDEX)
                .setTypes(E_TYPE)
                .setSearchType(SearchType.DFS_QUERY_THEN_FETCH);


        BoolQueryBuilder q = QueryBuilders.boolQuery();

        q.must(QueryBuilders.matchQuery("name", name));

        rb.setQuery(q);

        SearchResponse response = rb.execute().actionGet();

        if (response.getHits().getTotalHits() > 0) {
            String dataJson = response.getHits().getAt(0).getSourceAsMap().get("data").toString();
            result = gson.fromJson(dataJson, Account.class);
        }

        return result;
    }

    public Account save (Account account) throws Exception {

        this.logger.info("save");

        indexAccount(account);

        return account;
    }

    public Account delete (long id) {
        return null;
    }


    public void indexAccount(Account account) {

        Map<String, Object> json = new HashMap<String, Object>();

        if (account.getId() == null) {
            account.setId(CommonUtils.getSystemTimestamp());
        }

        /*if(account.getAddDate() == null){
            account.setAddDate(getUnixTimestamp());
        }*/

        json.put("id", account.getId());
        json.put("name", account.getName());
        json.put("location", account.getLocation());

        json.put(E_DATAFIELD, gson.toJson(account));

        IndexResponse response = this.elasticClient.prepareIndex(E_INDEX, E_TYPE, Long.toString(account.getId())).setSource(json).get();
    }
}
