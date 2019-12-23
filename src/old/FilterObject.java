package utils;

import java.util.*;

public class FilterObject {

    public String fieldName;

    public Integer exactVal;
    public Integer lowerVal;
    public Integer upperVal;
    public List<String> arrayVal;

    public FilterObject (String fieldName, Integer exactVal) {
        this.fieldName = fieldName;
        this.exactVal = exactVal;
        this.lowerVal = null;
        this.upperVal = null;
        this.arrayVal = null;
    }

    public FilterObject (String fieldName, Integer lowerVal, Integer upperVal) {
        this.fieldName = fieldName;
        this.exactVal = exactVal;
        this.lowerVal = lowerVal;
        this.upperVal = upperVal;
        this.arrayVal = null;
    }

    public FilterObject (String fieldName, List<String> arrayVal) {
        this.fieldName = fieldName;
        this.exactVal = null;
        this.lowerVal = null;
        this.upperVal = null;
        this.arrayVal = arrayVal;
    }

    public String toString() {
        return this.fieldName + "("+this.exactVal+","+this.lowerVal+","+this.upperVal+");";
    }

    public static String toString(List<FilterObject> list) {
        String res = ""+list.size()+":";

        for(FilterObject filt: list){
            String arrs;
            if(filt.arrayVal != null){
                arrs="{\n\t\t";
                for (String s : filt.arrayVal){
                    arrs += s + ",\n\t\t";
                }
                arrs = arrs.substring(0, arrs.length()-4);
                arrs += "\n\t}";
            } else arrs = "{}";
            res += filt.fieldName + "(\n\t"+filt.exactVal+",\n\t"+filt.lowerVal+",\n\t"+filt.upperVal+",\n\t"+arrs+"\n);";
        }
        return res;
    }

}
