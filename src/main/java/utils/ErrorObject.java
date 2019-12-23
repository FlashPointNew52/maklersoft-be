package utils;

public class ErrorObject {
    public String code;
    public String msg;
    public Object obj;

    public ErrorObject (String code, String msg) {
        this.code = code;
        this.msg = msg;
        this.obj = null;
    }

    public ErrorObject (String code, String msg, String obj) {
        this.code = code;
        this.msg = msg;
        this.obj = obj;
    }
    @Override
    public String toString() {
        return "Error " + this.code + ": " + this.msg + " \n";
    }

}
