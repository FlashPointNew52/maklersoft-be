package auxclass;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class Rating {
    private Map<String, Float> map;

    public Rating(){
            this.map = new HashMap<String, Float>(){{put("average", 0f);}};
    }
}
