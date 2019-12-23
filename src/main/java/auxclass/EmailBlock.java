package auxclass;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

public class EmailBlock {
    @Getter
    @Setter
    private String work;
    @Getter
    @Setter
    private String main;
    @Getter
    @Setter
    private String other;

    public EmailBlock(){}

    public EmailBlock(String[] mails){
        if(mails != null)
            for(int i = 0; i < mails.length; ++i){
                 switch(i){
                    case 0: this.main = EmailBlock.normaliseMail(mails[0]); break;
                    case 1: this.work = EmailBlock.normaliseMail(mails[1]); break;
                    case 2: this.other = EmailBlock.normaliseMail(mails[2]); break;
                 }
            }
    }

    public List<String> getAsList() {
        List<String> result = new ArrayList<>();

        if (work != null) result.add(normaliseMail(work));
        if (main != null) result.add(normaliseMail(main));
        if (other != null) result.add(normaliseMail(other));

        return result;
    }

    public String getAsString(String delim){
        if(getAsList().size() != 0)
          return String.join(delim, getAsList());
        return null;
    }

    public static String normaliseMail(String mail){
        return mail.toLowerCase();
    }

    public boolean equals(EmailBlock o) {
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
