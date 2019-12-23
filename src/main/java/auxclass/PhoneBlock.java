package auxclass;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
@Data
public class PhoneBlock {
    private String office;
    private String home;
    private String cellphone;
    private String fax;
    private String main;
    private String other;
    private String ip;
    public PhoneBlock(){}
    public PhoneBlock(String[] phones){
        for(int i = 0; i < phones.length; ++i){
             switch(i){
                case 0: this.main = PhoneBlock.normalisePhoneTrunc(phones[0]); break;
                case 1: this.office = PhoneBlock.normalisePhoneTrunc(phones[1]); break;
                case 2: this.cellphone = PhoneBlock.normalisePhoneTrunc(phones[2]); break;
                case 3: this.home = PhoneBlock.normalisePhoneTrunc(phones[3]); break;
                case 4: this.other = PhoneBlock.normalisePhoneTrunc(phones[4]); break;
                case 5: this.fax = PhoneBlock.normalisePhoneTrunc(phones[5]); break;
             }
        }
    }

    public List<String> getAsList() {
        List<String> result = new ArrayList<>();

        if (office != null && office.length() != 0) result.add(normalisePhone(office));
        if (home != null && home.length() != 0) result.add(normalisePhone(home));
        if (cellphone != null && cellphone.length() != 0) result.add(normalisePhone(cellphone));
        if (fax != null && fax.length() != 0) result.add(normalisePhone(fax));
        if (main != null && main.length() != 0) result.add(normalisePhone(main));
        if (other != null && other.length() != 0) result.add(normalisePhone(other));
        if (ip != null && ip.length() != 0) result.add(normalisePhone(ip));

        return normalisePhones(result);
    }

    public boolean equals(PhoneBlock o) {
        if (o == this) return true;
        if (o == null) return false;

        if (o.office == null && office != null)                     return false;
        if (o.office != null && !o.office.equals(office))           return false;
        if (o.home == null && home != null)                         return false;
        if (o.home != null && !o.home.equals(home))                 return false;
        if (o.fax == null && fax != null)                           return false;
        if (o.fax != null && !o.fax.equals(fax))                    return false;
        if (o.cellphone == null && cellphone != null)               return false;
        if (o.cellphone != null && !o.cellphone.equals(cellphone))  return false;
        if (o.main == null && main != null)                         return false;
        if (o.main != null && !o.main.equals(main))                 return false;
        if (o.other == null && other != null)                       return false;
        if (o.other != null && !o.other.equals(other))              return false;
        if (o.ip == null && ip != null)                             return false;
        if (o.ip != null && !o.ip.equals(other))                    return false;

        return true;
    }

    public static List<String> normalisePhones(List<String> phones){
        List<String> temp = new ArrayList<>();
        for (String phone: phones) {
            temp.add(normalisePhone(phone));
        }
        return temp;
    }

    public static List<String> normalisePhonesTrunc(List<String> phones){
        List<String> temp = new ArrayList<>();
        for (String phone: phones) {
            temp.add(normalisePhoneTrunc(phone));
        }
        return temp;
    }

    public static String normalisePhone(String phone){
        String temp = phone.replaceAll("[^\\d]", "");
        if(temp.length() == 11 && temp.charAt(0) == '8')
            temp = temp.replaceFirst("8", "7");
        else if(temp.length() == 10)
            temp = "7" + temp;
        return temp;
    }

    public static String normalisePhoneTrunc(String phone){
        String temp = phone.replaceAll("[^\\d]", "");
        if(temp.length() == 11)
            temp = temp.substring(1);
        return temp;
    }
}
