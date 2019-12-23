package entity;

import lombok.Getter;
import lombok.Setter;


public class Account {

    public Account(){}
    public Account(String name){
        this.name = name;
        this.location = "msk";
    }

    @Getter
    @Setter
    private Long id;

    @Getter
    @Setter
    private String name;

    @Getter
    @Setter
    private String location;

    @Getter
    @Setter
    private Long last_login;

    @Getter
    @Setter
    private Long add_date;

    @Getter
    @Setter
    private float balance;

}
