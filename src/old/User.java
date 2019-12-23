package entity;

import auxclass.*;
import lombok.Getter;
import lombok.Setter;
import entity.Person;

import static utils.CommonUtils.getUnixTimestamp;

public class User {
    @Getter
    @Setter
    private Long id;
    @Getter
    @Setter
    private Long accountId;   //ID организации-аккаунта
    @Getter
    @Setter
    private String password;
    @Getter
    @Setter
    private String name;
    @Getter
    @Setter
    private Long agentId;   //Ответственный
    @Getter
    @Setter
    private User agent;
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
    private Long assignDate;
    @Getter
    @Setter
    private String position;
    @Getter
    @Setter
    private String department;
    @Getter
    @Setter
    private String stateCode;
    @Getter
    @Setter
    private Long organisationId;
    @Getter
    @Setter
    private Organisation organisation;
    @Getter
    @Setter
    private String specialization;
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
    private String description;
    @Getter
    @Setter
    private String photo;
    @Getter
    @Setter
    private String photoMini;
    @Getter
    @Setter
    private String tag;
    @Getter
    @Setter
    private float rate;
    @Getter
    @Setter
    private String temp_code;
    @Getter
    @Setter
    private Long date_of_temp;
    @Getter
    @Setter
    private Long persRef;
    @Getter
    @Setter
    private String entryState; //Поле статуса пользователя: reg - неактивный пользователь, который зарегистрировавший компанию
                               //                           new - новый пользователь, добавленый через программу
                               //                           confirm - подтвержденный пользователь
                               //                           main - основной пользователь-владелец

    public User(){
        newBlocks();
    }

    public User(String name){
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

    void preIndex() {
        if (getId() == null) {
            setAddDate(getUnixTimestamp());
        }
        setChangeDate(getUnixTimestamp());

    }

    public String getPhonesString(){
        if(this.phoneBlock.getAsList().size() > 0)
            return String.join(",", this.phoneBlock.getAsList());
        else return null;
    }

    public Person toPerson(){
        Person pers = new Person();
        pers.setId(id);
        pers.setAccountId(accountId);
        pers.setName(name);
        pers.setAddDate(addDate);
        pers.setChangeDate(changeDate);
        pers.setStateCode(stateCode);
        pers.setTypeCode("realtor");
        pers.setAddressBlock(addressBlock);
        pers.setPhoneBlock(phoneBlock);
        pers.setEmailBlock(emailBlock);
        pers.setSiteBlock(siteBlock);
        pers.setSocialBlock(socialBlock);
        pers.setMessengerBlock(messengerBlock);
        pers.setDescription(description);
        pers.setOrganisation(organisation);
        pers.setOrganisationId(organisationId);
        pers.setPhoto(photo);
        pers.setPhotoMini(photoMini);
        pers.setRate(rate);
        pers.setUserRef(persRef);
        return pers;
    }
}
