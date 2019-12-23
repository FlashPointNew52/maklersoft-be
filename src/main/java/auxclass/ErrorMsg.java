package auxclass;

import entity.User;
import lombok.Data;

@Data
public class ErrorMsg {
    private Integer type; //0 - системная; 1 - валидация значений
    private String error;
    private String message;
    private String name;
    private Boolean ok;
    private Integer status;
    private String statusText;
    private String url;
    private User user;
    private Object addData;

    public ErrorMsg(){
    }

    public ErrorMsg(String error, String message, String name, Integer type){
        this.error = error;
        this.message = message;
        this.name = name;
        this.type = type;
    }

    public ErrorMsg(String error, String message, String name, Integer type, Object addData){
        this.error = error;
        this.message = message;
        this.name = name;
        this.type = type;
        this.addData = addData;
    }
}
