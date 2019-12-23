package auxclass;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

public class SiteBlock {
    @Getter
    @Setter
    private String work;
    @Getter
    @Setter
    private String main;
    @Getter
    @Setter
    private String other;

    public List<String> getAsList() {
        List<String> result = new ArrayList<>();

        if (work != null) result.add(work);
        if (main != null) result.add(main);
        if (other != null) result.add(other);

        return result;
    }

    public boolean equals(SiteBlock o) {
        if (o == this) return true;
        if (o == null) return false;

        if (o.work == null && work != null)                     return false;
        if (o.work != null && !o.work.equals(work))             return false;
        if (o.main == null && main != null)                     return false;
        if (o.main != null && !o.main.equals(main))             return false;
        if (o.other == null && other != null)                   return false;
        if (o.other != null && !o.other.equals(other))          return false;
        return true;
    }
}
