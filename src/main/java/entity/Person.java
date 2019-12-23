package entity;

import auxclass.UploadFile;
import configuration.AppConfig;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.CommonUtils;

import static utils.CommonUtils.getUnixTimestamp;

@Data
public class Person extends Contact{
    private Boolean isMiddleman;
    private Long  userRef;
    private String loyalty;
    private Long agentOrgId;

    public void preIndex() {
        if (this.getId() == null) {
            this.setId(CommonUtils.getSystemTimestamp());
            this.setAddDate(getUnixTimestamp());
        }

        if(this.getIsMiddleman() == null)
            this.setIsMiddleman(false);

        this.setChangeDate(getUnixTimestamp());
        UploadFile uploadFile = new UploadFile("fdsfs",0L,true);
        try {
            if(getPhoto() != null && getPhoto().indexOf(AppConfig.FILE_STORAGE_URL + "temp/") != -1){
                setPhoto(UploadFile.moveFromTemp(getPhoto(), "photo/persons/" + getAccountId() + "/" + getId(), true));
                setPhoto(uploadFile.minimazePhoto(getPhoto(), "photo/persons/" + getAccountId() + "/" + getId(), 512, 384));
                setPhotoMini(uploadFile.minimazePhoto(getPhoto(), "photo/persons/" + getAccountId() + "/" + getId() + "/mini", 256, 192));
            }
        } catch (Exception exp){

        }

    }

    public boolean equals(Person o) {
        /*Logger logger = LoggerFactory.getLogger(Person.class);
        if (o == this) return true;
        if (o == null)                                                                      {logger.info("null"); return false;}

        if (!o.id.equals(id))                                                               {logger.info("id "); return false;}
        if (!o.accountId.equals(accountId) )                                                {logger.info("accountId "); return false;}
        if (o.name == null && name != null)                                                 {logger.info("name"); return false;}
        if (o.name != null && !o.name.equals(name))                                         {logger.info("name"); return false;}
        if (o.description == null && description != null)                                   {logger.info("description"); return false;}
        if (o.description != null && !o.description.equals(description))                    {logger.info("description"); return false;}
        if (o.addDate == null && addDate != null)                                           {logger.info("addDate"); return false;}
        if (o.addDate != null && !o.addDate.equals(addDate))                                {logger.info("addDate"); return false;}
        if (o.organisationId == null && organisationId != null)                             {logger.info("organisationId"); return false;}
        if (o.organisationId != null && !o.organisationId.equals(organisationId))           {logger.info("organisationId"); return false;}
        if (o.addressBlock == null && addressBlock != null)                                 {logger.info("addressBlock"); return false;}
        if (o.addressBlock != null && !o.addressBlock.equals(addressBlock))                 {logger.info("addressBlock"); return false;}
        if (o.phoneBlock == null && phoneBlock != null)                                     {logger.info("phoneBlock"); return false;}
        if (o.phoneBlock != null && !o.phoneBlock.equals(phoneBlock))                       {logger.info("phoneBlock"); return false;}
        if (o.emailBlock == null && emailBlock != null)                                     {logger.info("emailBlock"); return false;}
        if (o.emailBlock != null && !o.emailBlock.equals(emailBlock))                       {logger.info("emailBlock"); return false;}
        if (o.siteBlock == null && siteBlock != null)                                       {logger.info("webSite"); return false;}
        if (o.siteBlock != null && !o.siteBlock.equals(siteBlock))                          {logger.info("webSite"); return false;}
        if (o.agentId == null && agentId != null)                                           {logger.info("agentId"); return false;}
        if (o.agentId != null && !o.agentId.equals(agentId))                                {logger.info("agentId"); return false;}
        if (o.stateCode == null && stateCode != null)                                       {logger.info("stateCode"); return false;}
        if (o.stateCode != null && !o.stateCode.equals(stateCode))                          {logger.info("stateCode"); return false;}
        if (o.sourceCode == null && sourceCode != null)                                     {logger.info("sourceCode"); return false;}
        if (o.sourceCode != null && !o.sourceCode.equals(sourceCode))                       {logger.info("sourceCode"); return false;}
        if (o.typeCode == null && typeCode != null)                                         {logger.info("typeCode"); return false;}
        if (o.typeCode != null && !o.typeCode.equals(typeCode))                             {logger.info("typeCode"); return false;}
*/
        return true;
    }
}
