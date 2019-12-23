package entity;


import lombok.Getter;
import lombok.Setter;

import utils.CommonUtils;
import entity.*;


import static utils.CommonUtils.getUnixTimestamp;

public class History {

    @Getter
    @Setter
    private Long id;

    @Getter
    @Setter
    private Long objectId;

    @Getter
    @Setter
    private String typeClass;

    @Getter
    @Setter
    private Long addDate;

    @Getter
    @Setter
    private Person person;

    @Getter
    @Setter
    private Offer offer;

    @Getter
    @Setter
    private Organisation organisation;

    @Getter
    @Setter
    private User user;

    public History(Long objectId, Long addDate, Person person) {
        this.id = CommonUtils.getSystemTimestamp();
        this.objectId = objectId;
        this.typeClass = "person";
        this.addDate = addDate;
        this.person = person;
        this.offer = null;
        this.organisation = null;
        this.user = null;
    }

    /*public History(Long objectId,Long addDate, User user) {
        this.objectId = objectId;
        this.typeClass = "user";
        this.addDate = addDate;
        this.user = user;
    }

    public History(Long objectId, Long addDate, Offer offer) {
        this.objectId = objectId;
        this.typeClass = "offer";
        this.addDate = addDate;
        this.offer = offer;
    }

    public History(Long objectId, String typeClass, Long addDate, Organisation organisation) {
        this.objectId = objectId;
        this.typeClass = "organisation";
        this.addDate = addDate;
        this.organisation = organisation;
    }*/
}
