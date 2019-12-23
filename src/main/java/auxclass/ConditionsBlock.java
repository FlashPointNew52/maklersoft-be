package auxclass;

import lombok.Data;
import utils.CommonUtils;

@Data
public class ConditionsBlock {
    private boolean complete;
    private boolean living_room_furniture;
    private boolean kitchen_furniture;
    private boolean couchette;
    private boolean bedding;
    private boolean dishes;
    private boolean refrigerator;
    private boolean washer;
    private boolean microwave_oven;
    private boolean air_conditioning;
    private boolean dishwasher;
    private boolean tv;
    private boolean with_animals;
    private boolean with_children;

    ConditionsBlock(){
        this.complete = false;
        this.living_room_furniture = false;
        this.kitchen_furniture = false;
        this.couchette = false;
        this.bedding = false;
        this.dishes = false;
        this.refrigerator = false;
        this.washer = false;
        this.microwave_oven = false;
        this.air_conditioning = false;
        this.dishwasher = false;
        this.tv = false;
        this.with_animals = false;
        this.with_children = false;
    }

    public void setComplete(){
        this.complete = true;
        this.living_room_furniture = true;
        this.kitchen_furniture = true;
        this.couchette = true;
        this.bedding = true;
        this.dishes = true;
        this.refrigerator = true;
        this.washer = true;
        this.microwave_oven = true;
        this.air_conditioning = true;
        this.dishwasher = true;
        this.tv = true;
        this.with_animals = true;
        this.with_children = true;
    }

    public void setNullValues(){
        this.complete = CommonUtils.strNotNull(this.complete);
        this.living_room_furniture = CommonUtils.strNotNull(this.living_room_furniture);
        this.kitchen_furniture = CommonUtils.strNotNull(this.kitchen_furniture);
        this.couchette = CommonUtils.strNotNull(this.couchette);
        this.bedding = CommonUtils.strNotNull(this.bedding);
        this.dishes = CommonUtils.strNotNull(this.dishes);
        this.refrigerator = CommonUtils.strNotNull(this.refrigerator);
        this.washer = CommonUtils.strNotNull(this.washer);
        this.microwave_oven = CommonUtils.strNotNull(this.microwave_oven);
        this.air_conditioning = CommonUtils.strNotNull(this.air_conditioning);
        this.dishwasher = CommonUtils.strNotNull(this.dishwasher);
        this.tv = CommonUtils.strNotNull(this.tv);

        this.with_animals = CommonUtils.strNotNull(this.with_animals);
        this.with_children = CommonUtils.strNotNull(this.with_children);
    }
}
