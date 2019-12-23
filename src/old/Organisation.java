package entity;

import auxclass.*;
import lombok.Getter;
import lombok.Setter;

import static utils.CommonUtils.getUnixTimestamp;

public class Organisation {

    @Getter
    @Setter
    private Long id;
    @Getter
    @Setter
    private Long accountId;
    @Getter
    @Setter
    private String name;

    @Getter
    @Setter
    private Long addDate;
    @Getter
    @Setter
    private Long changeDate;
    @Getter
    @Setter
    private Long archiveDate;

    @Getter
    @Setter
    private String typeCode;

    @Getter
    @Setter
    private AddressBlock addressBlock;
    @Getter
    @Setter
    private PhoneBlock phoneBlock;
    @Getter
    @Setter
    private EmailBlock emailBlock;
    @Getter
    @Setter
    private SiteBlock siteBlock;
    @Getter
    @Setter
    private SocialBlock socialBlock;
    @Getter
    @Setter
    private MessengerBlock messengerBlock;

    @Getter
    @Setter
    private Person contact;
    @Getter
    @Setter
    private Long contactId;

    @Getter
    @Setter
    private String stateCode;

    @Getter
    @Setter
    private String sourceCode;

    @Getter
    @Setter
    private Long agentId;

    @Getter
    @Setter
    private User agent;

    @Getter
    @Setter
    private String goverType;

    @Getter
    @Setter
    private Organisation main_office;
    @Getter
    @Setter
    private Long main_office_id;

    @Getter
    @Setter
    private String description;
    @Getter
    @Setter
    private String tag;
    @Getter
    @Setter
    private Boolean isAccount;
    @Getter
    @Setter
    private float rate;
    @Getter
    @Setter
    private Long orgRef;

    public Organisation(){
        this.addressBlock = new AddressBlock();
        this.phoneBlock = new PhoneBlock();
        this.emailBlock = new EmailBlock();
        this.siteBlock = new SiteBlock();
        this.socialBlock = new SocialBlock();
        this.messengerBlock = new MessengerBlock();
        this.goverType = "main";
    }

    void preIndex() {
        if (getId() == null) {
            setAddDate(getUnixTimestamp());
        }
        setChangeDate(getUnixTimestamp());
    }
}
