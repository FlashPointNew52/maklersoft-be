package utils;

import java.util.LinkedList;
import java.util.List;

public class ParseResult {
    public String query;
    public List<FilterObject> filterList;

    ParseResult() {
        filterList = new LinkedList<>();
    }
}
