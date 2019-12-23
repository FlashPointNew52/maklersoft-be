package entity;

import auxclass.*;
import lombok.Data;
import utils.FilterObject;

import java.util.*;
import utils.CommonUtils;

@Data
public class Request {
    private Long id;
    private Long accountId;

    private Long agentId;
    private User agent;
    private Long personId;
    private Person person;
    private Long companyId;
    private Organisation company;

    private String categoryCode;
    private String buildingType;
    private String buildingClass;
    private String offerTypeCode;
    private String objectStage;
    private String distance;
    private String settlement;
    private Boolean guard;
    private String housingComplex;
    private Boolean mortgages;
    private String houseType;
    private String roomScheme;
    private Integer floorsCount;
    private Integer levelsCount;

    private Float squareTotal;
    private Float squareLiving;
    private Float squareKitchen;
    private Boolean balcony;
    private Boolean loggia;
    private String condition;
    private String bathroom;
    private Float squareLand;
    private String squareLandType;
    private Boolean waterSupply;
    private Boolean gasification;
    private Boolean electrification;
    private Boolean sewerage;
    private Boolean centralHeating;
    private String objectName;
    private Float ceilingHeight;
    private Boolean lift;
    private Boolean parking;
    private auxclass.Rating locRating;

    private Long addDate;
    private Long changeDate;
    private Long assignDate;
    private Long arrival_date;

    private Boolean newBuilding;
    private Boolean encumbrance;
    private String buildYear;
    private String rate;

    private Integer roomsCount;
    private Integer floor;

    private String stageCode;



    private String landPurpose;







    private ContractBlock contractBlock;
    private Integer tag;
    private String costInfo;
    private String description;

    //Аренда
    private ConditionsBlock conditions;
    private String period;
    private String paymentMethod;
    private Boolean deposit;
    private Boolean utilityBills;
    private Boolean commission;
    private Boolean counters;
    //Не аренда
    private Boolean cash;
    private Boolean mortgage;
    private Boolean certificate;
    private Boolean maternalCapital;

    private GeoPoint[] searchArea;
    private ValueRange budget;
    private ValueRange square;
    private String typeCode;

    private ArrayList<UploadFile> documents;

    public void preIndex() {
        if (this.id == null) {
            this.id = CommonUtils.getSystemTimestamp();
            this.addDate = CommonUtils.getUnixTimestamp();
        }
        if(this.agentId == null)
            this.agent = null;
        if(this.personId == null)
            this.person = null;
        if(this.companyId == null)
            this.company = null;

        this.changeDate = CommonUtils.getUnixTimestamp();
        this.stageCode = this.stageCode == null ? "raw": this.stageCode;
        this.newBuilding = this.newBuilding == null ? false : this.newBuilding;
        this.encumbrance = this.encumbrance == null ? false : this.encumbrance;
        this.costInfo = this.costInfo == null ? "" : this.costInfo;
        this.description = this.description == null ? "" : this.description;

        if(this.offerTypeCode == "rent"){
            this.deposit = this.deposit == null ? false : this.deposit;
            this.utilityBills = this.utilityBills == null ? false : this.utilityBills;
            this.commission = this.commission == null ? false : this.commission;
            this.counters = this.counters == null ? false : this.counters;

            this.deposit = this.deposit == null ? false : this.deposit;
            // Обнуление полей для "не аренды"
            this.cash = this.mortgage = this.certificate = this.maternalCapital = null;
        } else {
            this.cash = this.cash == null ? false : this.cash;
            this.mortgage = this.mortgage == null ? false : this.mortgage;
            this.certificate = this.certificate == null ? false : this.certificate;
            this.maternalCapital = this.maternalCapital == null ? false : this.maternalCapital;
            // Обнуление полей аренды
            this.deposit = this.utilityBills = this.commission = this.counters = null;
        }
        if(this.documents == null)
            this.documents = new ArrayList<>();
        this.documents = UploadFile.moveFromTemp(this.documents, "docs/requests/" + getAccountId() + "/" + getId());
    }
}
