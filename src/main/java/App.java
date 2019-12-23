import configuration.AppConfig;

import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.transport.client.PreBuiltTransportClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import resource.*;
import service.*;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static spark.Spark.exception;


public class App {
    static Logger logger = LoggerFactory.getLogger(App.class);

    static TransportClient ec;

    static AccountService accountService;
    static UserService userService;
    static OrganisationService orgService;
    static PersonService personService;
    static OfferService offerService;
    static RequestService requestService;
    static GeoService geoService;
    static UploadService uploadService;
    static HistoryService historyService;
    static CommentService commentService;
    static RatingService ratingService;

    public static void main(String[] args) throws Exception {

        AppConfig.LoadConfig();
        //Spark.externalStaticFileLocation(AppConfig.STATIC_FILE_LOCATION);

        ec = getElasticClient();

        // services
        App.historyService = new HistoryService(ec);
        App.accountService = new AccountService(ec);
        App.ratingService = new RatingService(ec);
        App.personService = new PersonService(ec, historyService,ratingService);
        App.userService = new UserService(ec,ratingService);
        App.orgService = new OrganisationService(ec, personService, ratingService);
        App.ratingService.setPersonService(personService);
        App.ratingService.setUserService(userService);
        App.offerService = new OfferService(ec, userService, personService, orgService, historyService);
        App.requestService = new RequestService(ec, userService, personService, orgService, historyService);
        App.geoService = new GeoService();
        App.uploadService = new UploadService();
        App.commentService = new CommentService(ec, personService, userService);


        // resources
        new Authorisation();
        new SocialResource();
        new Maintenance(App.offerService, App.userService, App.personService);
        new AccountResource(App.accountService);
        new UserResource(App.userService, App.offerService, App.requestService, App.personService, App.orgService, App.commentService, App.ratingService);
        new OrganisationResource(App.orgService, App.offerService, App.requestService, App.personService, App.userService, App.commentService, App.ratingService);
        new PersonResource(App.personService, App.userService, App.orgService, App.offerService, App.requestService, App.ratingService, App.commentService);
        new OfferResource(App.offerService);
        new RequestResource(App.requestService);
        new GeoResource(App.geoService);
        new UploadResource(App.uploadService);
        new HistoryResource(App.historyService);
        new CommentResource(App.commentService, App.personService, App.userService, App.orgService);
        new RatingResource(App.ratingService, App.personService, App.userService, App.orgService);

        exception(Exception.class, (exception, request, response) -> {
            response.status(500);
            response.body("Ex:" + exception.getMessage());
            exception.printStackTrace();
        });
    }

    private static TransportClient getElasticClient() throws UnknownHostException {

        Settings settings = Settings.builder().put("cluster.name", "rplus-dev").build();
        TransportClient client = new PreBuiltTransportClient(settings).addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName("localhost"), 9300));

        return client;
    }
}
