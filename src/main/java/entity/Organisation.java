package entity;

import auxclass.GeoPoint;
import auxclass.UploadFile;
import lombok.Data;
import lombok.Builder;
import utils.CommonUtils;

import static utils.CommonUtils.getUnixTimestamp;

@Data
public class Organisation extends Contact{
    private Boolean isMiddleman;
    private Person contact;
    private Long contactId;
    private String goverType;
    private Organisation main_office;
    private Long main_office_id;
    private Boolean isAccount;
    private Long orgRef;
    private Boolean ourCompany;
    private GeoPoint location;

    public Organisation(){
        super();
        this.goverType = "main";
        this.isMiddleman = true;
        this.setTypeCode("optimally");
        this.setStateCode("undefined");
    }

    public Organisation(String name){
        super(name);
    }

    public void preIndex() {
        if (getId() == null) {
            setAddDate(getUnixTimestamp());
            setId(CommonUtils.getSystemTimestamp());
        }
        setChangeDate(getUnixTimestamp());
        if(!getId().equals(getAccountId()))
            setIsAccount(false);
        else
            setIsAccount(true);
        if(getTypeCode() == null)
            setTypeCode("client");
        if(getStateCode() == null)
            setStateCode("undefined");
        if(getGoverType() == null)
            setGoverType("main");
        if(getId().equals(getAccountId())){
            isAccount = true;
            ourCompany = true;
            isMiddleman = true;
        }
        if(isMiddleman == null)
            isMiddleman = false;
        if(main_office != null && main_office_id == null){
            main_office_id = main_office.getId();
        }
        if(main_office != null &&
            (      main_office.isAccount && main_office_id.equals(getAccountId())
                || main_office.ourCompany && main_office.getAccountId().equals(getAccountId())
            )
        ){
            ourCompany = true;
            isMiddleman = true;
        } else if(getId().equals(getAccountId())) {
            isAccount = true;
            ourCompany = true;
            isMiddleman = true;
        } else {
            ourCompany = false;
        }
        setPhoto(UploadFile.moveFromTemp(getPhoto(), "photo/orgs/" + getAccountId() + "/" + getId(), true));
        setPhotoMini(getPhoto());
    }
}
