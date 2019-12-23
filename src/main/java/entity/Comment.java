package entity;

import lombok.Getter;
import lombok.Setter;


public class Comment {

    @Getter
    @Setter
    private Long id;	        //	Идентификатор комментария

    @Getter
    @Setter
    private String  text;		//Текст комментария

    @Getter
    @Setter
    private String[] phones;       //Телефон контакта

    @Getter
    @Setter
    private Long add_date;      //Дата добавления комментария

    @Getter
    @Setter
    private Integer like_count; //Количество лайков

    @Getter
    @Setter
    private Long[] like_users;  //Массив ID пользователей с лайками

    @Getter
    @Setter
    private Integer dislike_count;  //Количество дизлайков

    @Getter
    @Setter
    private Long[] dislike_users;  //Массив ID пользователей с дизлайками

    @Getter
    @Setter
    private Long agentId;      //Идентификатор пользователя, который оставил комментарий

    @Getter
    @Setter
    private Long objId;      //ID контакта, используется для поиска дублей

    @Getter
    @Setter
    private String objType;      //ID контакта, используется для поиска дублей

}
