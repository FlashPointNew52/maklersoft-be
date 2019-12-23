package service;

import auxclass.PhoneBlock;
import com.google.gson.Gson;
import entity.Comment;
import entity.Organisation;
import entity.Person;
import entity.User;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.DeleteByQueryRequestBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.DeleteByQueryAction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.CommonUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Arrays;

import static utils.CommonUtils.getUnixTimestamp;

public class CommentService {
    private final String E_INDEX = "ms_comment";
    private final String E_TYPE = "comments";
    private final String E_DATAFIELD = "data";

    Logger logger = LoggerFactory.getLogger(CommentService.class);
    private final Client elasticClient;
    private final PersonService personService;
    private final UserService userService;

    Gson gson = new Gson();

    public CommentService(Client elasticClient, PersonService personService, UserService userService) {
        this.elasticClient = elasticClient;
        this.personService = personService;
        this.userService = userService;
    }

    public class ListResult {
        long hitsCount;
        List<Comment> list;
    }

    public ListResult list (Long accountId, int page, int perPage, List<String> phones, Long userId, Long addDate, String type) {

        List<Comment> commentList = new ArrayList<>();

        SearchRequestBuilder rb = elasticClient.prepareSearch(E_INDEX)
                .setTypes(E_TYPE)
                .setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
                .setFrom(page * perPage).setSize(perPage);


        BoolQueryBuilder q = QueryBuilders.boolQuery();
        ListResult r = new ListResult();

        List<String> person_phones = new ArrayList<>();

        if (userId != null)
            q.must(QueryBuilders.termQuery("agentId", userId));
        if (addDate != null)
            q.must(QueryBuilders.termQuery("add_date", addDate));

        BoolQueryBuilder ph = QueryBuilders.boolQuery();
        for (String phone : phones) {
            ph.should(QueryBuilders.termQuery("phones", phone));
        }
        q.must(ph);

        if(type.equals("organisation")){
            q.must(QueryBuilders.termQuery("objType", type));
        } else{
            q.mustNot(QueryBuilders.termQuery("objType", "organisation"));
        }

        rb.setQuery(q);

        //this.logger.info(q.toString());

        rb.addSort(SortBuilders.fieldSort("add_date").order(SortOrder.DESC));
        rb.addSort(SortBuilders.fieldSort("agentId").order(SortOrder.DESC));
        SearchResponse response = rb.execute().actionGet();

        r.hitsCount = response.getHits().getTotalHits();
        for (SearchHit sh: response.getHits()) {
            String dataJson = sh.getSourceAsMap().get(E_DATAFIELD).toString();
            commentList.add(gson.fromJson(dataJson, Comment.class));
        }
        r.list = commentList;
        return r;
    }

    public Comment save (Comment comment) throws Exception {

        index(comment);

        return comment;
    }

    private void index(Comment comment) {

        Map<String, Object> json = new HashMap<String, Object>();

        if(comment.getId() == null) {
            comment.setId(CommonUtils.getSystemTimestamp());
            comment.setAdd_date(CommonUtils.getUnixTimestamp());
            comment.setDislike_count(0);
            comment.setDislike_users(new Long[]{});
            comment.setLike_count(0);
            comment.setLike_users(new Long[]{});
        }

        json.put("id", comment.getId());
        json.put("agentId", comment.getAgentId());
        json.put("objId", comment.getObjId());
        json.put("add_date", comment.getAdd_date());
        json.put("phones", comment.getPhones());
        json.put("objType", comment.getObjType());
        json.put(E_DATAFIELD, gson.toJson(comment));

        IndexResponse response = this.elasticClient.prepareIndex(E_INDEX, E_TYPE, Long.toString(comment.getId())).setSource(json).get();
    }

    public Comment get (Long id) throws Exception {
        Comment result = null;
        GetResponse response = this.elasticClient.prepareGet(E_INDEX, E_TYPE, Long.toString(id)).get();
        String dataJson = response.getSourceAsMap().get(E_DATAFIELD).toString();
        result = gson.fromJson(dataJson, Comment.class);
        return result;
    }

    public String delete (String id) throws Exception {

        this.logger.info("delete");
        DeleteResponse response = this.elasticClient.prepareDelete(E_INDEX, E_TYPE, id).get();

        return response.status().toString();

    }

    public boolean estimate(Long comment_id, Long user_id, Boolean estimate) {
        try {

            this.logger.info("estimate");
            Comment comment = get(comment_id);
            List<Long> list_id = new ArrayList<Long>(Arrays.asList(comment.getLike_users()));
            if(list_id.contains(user_id)){ //лайк стоит уже или дизлайк, тогда снимаем его
                list_id.remove(user_id);
            } else{ //если лайка нет
                if (estimate){ //если лайкто проставляем
                    list_id.add(user_id);
                }
            }
            comment.setLike_users(list_id.toArray(new Long[list_id.size()]));
            comment.setLike_count(list_id.size());

            list_id = new ArrayList<Long>(Arrays.asList(comment.getDislike_users()));
            if(list_id.contains(user_id)){ //дизлайк стоит уже или лайк, тогда снимаем его
                list_id.remove(user_id);
            } else{ //если дизлайка нет
                if (!estimate){ //если дизлайк то проставляем
                    list_id.add(user_id);
                }
            }
            comment.setDislike_users(list_id.toArray(new Long[list_id.size()]));
            comment.setDislike_count(list_id.size());
            index(comment);
            return true;
        } catch (Exception ex) {
            this.logger.info("estimate ex " + ex.toString());
            return false;
        }

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
