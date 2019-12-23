package auxclass;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

public class ContractBlock {
    @Getter
    @Setter
    private String number;
    @Getter
    @Setter
    private String begin;
    @Getter
    @Setter
    private String end;
    @Getter
    @Setter
    private String contined;
    @Getter
    @Setter
    private String terminated;

    public List<String> getAsList() {
        List<String> result = new ArrayList<>();

        if (number != null) result.add(number);
        if (begin != null) result.add(begin);
        if (end != null) result.add(end);
        if (contined != null) result.add(contined);
        if (terminated != null) result.add(terminated);
        return result;
    }

    public boolean equals(ContractBlock o) {
        if (o == this) return true;
        if (o == null) return false;

        if (o.number == null && number != null)                     return false;
        if (o.number != null && !o.number.equals(number))           return false;
        if (o.begin == null && begin != null)                       return false;
        if (o.begin != null && !o.begin.equals(begin))              return false;
        if (o.end == null && end != null)                           return false;
        if (o.end != null && !o.end.equals(end))                    return false;
        if (o.contined == null && contined != null)                 return false;
        if (o.contined != null && !o.contined.equals(contined))     return false;
        if (o.terminated == null && terminated != null)             return false;
        if (o.terminated != null && !o.terminated.equals(terminated)) return false;
        return true;
    }
}
