package entity;

import auxclass.*;
import lombok.EqualsAndHashCode;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.text.SimpleDateFormat;
import java.util.*;
import java.lang.reflect.Field;
import java.text.ParseException;
import utils.CommonUtils;
import static utils.CommonUtils.getUnixTimestamp;

@Data
@EqualsAndHashCode(exclude={"agent", "person", "openDate", "organisation"}, doNotUseGetters = true)
public class Offer {
    private Long id;
    private Long importId;
    private Long accountId;
    private String stageCode;
    private AddressBlock addressBlock;
    private String poi;
    private PhoneBlock phoneBlock;
    private EmailBlock emailBlock;
    private String houseType;
    private String roomScheme;
    private String condition;
    private String bathroom;
    private Float price;
    private Float ownerPrice;
    private Boolean deposit;
    private Float commission;
    private String commisionType;
    private String workInfo;
    private String description;
    private String costInfo;
    private String conditionInfo;
    private String sourceMedia;
    private String sourceUrl;
    private Long addDate;
    private Long openDate;
    private Long changeDate;
    private Long assignDate;
    private Long deleteDate;
    private Long lastSeenDate;
    private Long arrivalDate;

    private String period;
    private String mlsPriceType;
    private Float mlsPrice;
    private Long agentId;
    private User agent;
    private Long personId;
    private Person person;
    private Long companyId;
    private Organisation company;
    private GeoPoint location;
    private ArrayList<UploadFile> photos;
    private ArrayList<UploadFile> documents;
    private String documentsStr;
    private String sourceCode;
    private String offerTypeCode;
    private String rentType;
    private String categoryCode;
    private String typeCode;
    private String settlement;
    private String housingComplex;
    private String distance;
    private Boolean newBuilding;
    private String objectStage;
    private String buildYear;
    private Integer roomsCount;
    private Integer floor;
    private Integer floorsCount;
    private Integer levelsCount;
    private Float squareTotal;
    private Float squareLiving;
    private Float squareKitchen;
    private Float squareLand;
    private String squareLandType;
    private Boolean balcony;
    private Boolean loggia;
    private Boolean terrace;
    private Boolean guard;
    private Boolean waterSupply;
    private Boolean gasification;
    private Boolean electrification;
    private Boolean sewerage;
    private Boolean centralHeating;
    private Boolean lift;
    private Boolean parking;
    private String landPurpose;
    private String objectName;
    private String buildingType;
    private String buildingClass;
    private Float ceilingHeight;
    private ContractBlock contractBlock;
    private Boolean encumbrance;
    private Boolean mortgages;
    private Boolean certificate;
    private Boolean maternityCapital;
    private String tag;
    private String mediatorCompany;
    private String thirdPartyRights;
    private ConditionsBlock conditions;
    private Boolean prepayment;
    private String paymentType;
    private Boolean electrificPay;
    private Boolean waterPay;
    private Boolean gasPay;
    private Boolean heatingPay;
    private Boolean utilityBills;
    private Long offerRef;
    private auxclass.Rating locRating;

    public void preIndex() {
        if (this.id == null) {
            this.id = CommonUtils.getSystemTimestamp();
        }
        if (this.stageCode == null)
            this.stageCode = "raw";
        if(this.mortgages == null)
            this.mortgages = false;
        if(this.newBuilding == null)
            this.newBuilding = false;
        if(this.agentId == null)
            this.agent = null;
        if(this.personId == null)
            this.person = null;
        if(this.companyId == null)
            this.company = null;
        if(this.offerTypeCode.equals("rent")){
            this.getConditions().setNullValues();
            this.prepayment = CommonUtils.strNotNull(this.prepayment);
        }
        if(this.emailBlock == null){
            this.emailBlock = new EmailBlock();
        }
        if (this.addDate == null) {
            this.addDate = getUnixTimestamp();
            this.changeDate = getUnixTimestamp();
        }
        if(this.photos == null)
            this.photos = new ArrayList<>();
        if(this.documents == null)
            this.documents = new ArrayList<>();
        this.photos = UploadFile.moveFromTemp(this.photos, "photo/offers/" + getAccountId() + "/" + getId());
        this.documents = UploadFile.moveFromTemp(this.documents, "docs/offers/" + getAccountId() + "/" + getId());
    }
}
