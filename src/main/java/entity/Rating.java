package entity;

import lombok.Getter;
import lombok.Setter;

public class Rating {

    @Getter
    @Setter
    private Long id;	        //	Идентификатор

    @Getter
    @Setter
    private String[] phones;       //Телефон контакта

    @Getter
    @Setter
    private Long agentId;      //Идентификатор пользователя

    @Getter
    @Setter
    private Long objId;      //ID объекта

    @Getter
    @Setter
    private String objType;      //тип объекта

    @Getter
    @Setter
    private float avarege_mark;

    @Getter
    @Setter
    private float mark1;      //

    @Getter
    @Setter
    private float mark2;      //

    @Getter
    @Setter
    private float mark3;      //

    @Getter
    @Setter
    private float mark4;      //

    @Getter
    @Setter
    private float mark5;      //
}
