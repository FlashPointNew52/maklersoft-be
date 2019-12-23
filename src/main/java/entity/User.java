package entity;

import auxclass.UploadFile;
import lombok.Data;
import entity.Person;
import utils.CommonUtils;

import static utils.CommonUtils.getUnixTimestamp;

@Data
public class User extends Contact{
    private String position;
    private String department;
    private String specialization;
    private String category;
    private String password;
    private String temp_code;
    private Long date_of_temp;
    private Long persRef;
    private String entryState; //Поле статуса пользователя: reg - неактивный пользователь, который зарегистрировавший компанию
                               //                           new - новый пользователь, добавленый через программу
                               //                           confirm - подтвержденный пользователь
                               //                           main - основной пользователь-владелец

    public void preIndex() {
        if(getId() == null) {
            setId(CommonUtils.getSystemTimestamp());
            setAddDate(getUnixTimestamp());
        }
        setChangeDate(getUnixTimestamp());
        setPhoto(UploadFile.moveFromTemp(getPhoto(), "photo/users/" + getAccountId() + "/" + getId(), true));
        setPhotoMini(getPhoto());

    }
    public User(){ super();}

    public User(String name){
        super(name);
    }

    public Person toPerson(){
        Person pers = new Person();
        pers.setId(this.getId());
        pers.setAccountId(this.getAccountId());
        pers.setName(this.getName());
        pers.setAddDate(this.getAddDate());
        pers.setChangeDate(this.getChangeDate());
        pers.setTypeCode(this.getTypeCode());
        pers.setStateCode(this.getStateCode());
        pers.setAddressBlock(this.getAddressBlock());
        pers.setPhoneBlock(this.getPhoneBlock());
        pers.setEmailBlock(this.getEmailBlock());
        pers.setSiteBlock(this.getSiteBlock());
        pers.setSocialBlock(this.getSocialBlock());
        pers.setMessengerBlock(this.getMessengerBlock());
        pers.setDescription(this.getDescription());
        pers.setOrganisation(this.getOrganisation());
        pers.setOrganisationId(this.getOrganisationId());
        pers.setPhoto(this.getPhoto());
        pers.setPhotoMini(this.getPhotoMini());
        pers.setRate(this.getRate());
        pers.setUserRef(this.getPersRef());
        return pers;
    }
}
