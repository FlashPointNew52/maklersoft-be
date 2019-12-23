package entity;

import auxclass.GeoPoint;
import lombok.Getter;
import lombok.Setter;
import auxclass.ContractBlock;

import static utils.CommonUtils.getUnixTimestamp;

public class Request {

    @Getter
    @Setter
    private Long id;
    @Getter
    @Setter
    private Long accountId;

    @Getter
    @Setter
    private String request;

    @Getter
    @Setter
    private Long addDate;
    @Getter
    @Setter
    private Long changeDate;
    @Getter
    @Setter
    private Long assignDate;

    @Getter
    @Setter
    private Long agentId;
    @Getter
    @Setter
    private User agent;

    @Getter
    @Setter
    private Long personId;
    @Getter
    @Setter
    private Person person;

    @Getter
    @Setter
    private ContractBlock contractBlock;

    @Getter
    @Setter
    private String stateCode;

    @Getter
    @Setter
    private String sourceCode;

    @Getter
    @Setter
    private String stageCode;

    @Getter
    @Setter
    public String offerTypeCode;

    @Getter
    @Setter
    public Float cash;

    @Getter
    @Setter
    public Float mortgage;

    @Getter
    @Setter
    public Float certificate;

    @Getter
    @Setter
    public Float maternal_capital;

    @Getter
    @Setter
    public Float other_payment;

    @Getter
    @Setter
    public GeoPoint[] searchArea;

    @Getter
    @Setter
    private String description;

    @Getter
    @Setter
    private Float comission;
    @Getter
    @Setter
    private Float comissionPerc;

    @Getter
    @Setter
    private String tag;


    public void preIndex() {

        if (getId() == null) {
            setAddDate(getUnixTimestamp());
        }
    }

    public Float get_budget(){
        Float summ = 0.0f;
        if (this.cash != null)
            summ += cash;
        if (this.mortgage != null)
            summ += mortgage;
        if (this.certificate != null)
            summ += certificate;
        if (this.maternal_capital != null)
            summ += maternal_capital;
        if (this.other_payment != null)
            summ += other_payment;
        return summ;
    }
}
