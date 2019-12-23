package auxclass;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

public class Rating {
    @Getter
    @Setter
    private Map<String, Float> map;
}
