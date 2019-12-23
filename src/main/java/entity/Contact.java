package entity;
import auxclass.*;

import lombok.Data;
import lombok.Builder;

@Data
public class Contact {
    private Long id;
    private Long accountId;
    private String name;
    private String description;
    private Long addDate;
    private Long changeDate;
    private Long assignDate;
    private Long archiveDate;
    private AddressBlock addressBlock;
    private PhoneBlock phoneBlock;
    private EmailBlock emailBlock;
    private SiteBlock siteBlock;
    private SocialBlock socialBlock;
    private MessengerBlock messengerBlock;
    private Long agentId;
    private User agent;
    private Long organisationId;
    private Organisation organisation;
    private String typeCode;
    private String stateCode;
    private String stageCode;
    private String sourceCode;
    private String tag;
    private String photo;
    private String photoMini;

    private float rate;

    public Contact(){
        newBlocks();
    }

    public Contact(String name){
        this.name = name;
        newBlocks();
    }

    private void newBlocks(){
        this.addressBlock = new AddressBlock();
        this.phoneBlock = new PhoneBlock();
        this.emailBlock = new EmailBlock();
        this.siteBlock = new SiteBlock();
        this.socialBlock = new SocialBlock();
        this.messengerBlock = new MessengerBlock();
    }
}
