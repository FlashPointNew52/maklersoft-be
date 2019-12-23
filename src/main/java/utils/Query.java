package utils;

import auxclass.EmailBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.OfferService;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import auxclass.PhoneBlock;

public class Query {

    static Logger logger = LoggerFactory.getLogger(OfferService.class);

    public static HashMap<String, String> dTypeCode = new HashMap() {{
        put("доля", "share");
        put("комната", "room");
        put("квартира", "apartment");
        put("дом", "house");
        put("дача", "dacha");
        put("коттедж", "cottage");
        put("таунхаус", "townhouse");
        put("дуплекс", "duplex");
        put("садовый\\s*(земельный)?\\s*участок", "garden_land");
        put("огородный\\s*(земельный)?\\s*?участок", "cultivate_land");
        put("дачный\\s*(земельный)?\\s*участок", "dacha_land");
        put("отель", "hotel");
        put("ресторан", "restaurant");
        put("кафе", "сafe");
        put("спортивное\\s*сооружение", "sport_building");
        put("магазин", "shop");
        put("торговый\\s*центр", "shops_center");
        put("торгово\\s*развлекательный комплекс", "shop_entertainment");
        put("кабинет", "cabinet");
        put("офисное\\s*помещение", "office_space");
        put("офисное\\s*здание", "office_building");
        put("бизнес\\s*центр", "business_center");
        put("производственное\\s*здание", "manufacture_building");
        put("складское\\s*помещение", "warehouse_space");
        put("склад", "warehouse_space");
        put("промышленное\\s*предприятие", "industrial_enterprice");
        put("другое", "other");
    }};

    public static HashMap<String, String> dBuildingClass = new HashMap() {{
        put("(элит\\s*класс)", "elite");
        put("(бизнес\\s*класс)", "business");
        put("(эконом\\s*класс)", "economy");
        put("(новая\\s*планировка)", "new");
        put("(улучшенная)", "improved");
        put("(брежневка)", "brezhnev");
        put("(хрущевка)", "khrushchev");
        put("(сталинка)", "stalin");
        put("(старый\\s*фонд)", "old_fund");
        put("(индивидуальная)", "individual");
        put("(гостинка)", "gostinka");
        put("(общежитие)", "dormitory");
        put("(малосемейка)", "small_apartm");
    }};

    private static boolean regExTest (String regexp, String testString) {

        Pattern p = Pattern.compile(regexp);
        Matcher m = p.matcher(testString);
        return m.matches();

    }

    public static ParseResult parse (String query) {

        ParseResult r = new ParseResult();

        // Some commonly used regexes
        String sta_re = "(?:^|\\s+|,\\s*)";
        String end_re = "(?:\\s+|,|$)";
        String tofrom_re = "(?:от|до|с|со|по)";
        //type Code
        {
            List<String> res = new ArrayList<>();
            for (Map.Entry<String, String> pair: dTypeCode.entrySet()) {
                Pattern p = Pattern.compile(sta_re + pair.getKey() + end_re);
                Matcher m = p.matcher(query);
                if (m.find()){
                    res.add(pair.getValue());
                    query = query.replaceAll(pair.getKey(),"");
                    query = query.replaceAll("\\s{2,}"," ");
                }
            }

            Pattern p = Pattern.compile(sta_re + "(земля|участок)" + end_re);
            Matcher m = p.matcher(query);
            if (m.find()){
                res.add("garden_land");
                res.add("cultivate_land");
                res.add("dacha_land");
                query = m.replaceFirst("");
            }
            if(res.size() > 0)
                r.filterList.add(new FilterObject("typeCode", res));
        }
        //buildingClasse
        {
            List<String> res = new ArrayList<>();
            for (Map.Entry<String, String> pair: dBuildingClass.entrySet()) {
                Pattern p = Pattern.compile(sta_re + pair.getKey() + end_re);
                Matcher m = p.matcher(query);
                if (m.find()){
                    res.add(pair.getValue());
                    query = query.replaceAll(pair.getKey(),"");
                    query = query.replaceAll("\\s{2,}"," ");
                }
            }
            if(res.size() > 0)
                r.filterList.add(new FilterObject("buildingClass", res));
        }
        //new bilding
        {
            Pattern p = Pattern.compile(sta_re+"(новостройка|новая)"+end_re);
            Matcher m = p.matcher(query);
            if (m.find()){
                r.filterList.add(new FilterObject("newBuilding", 1));
                query = m.replaceFirst("");
            }
        }
        //mortgages
        {
            Pattern p = Pattern.compile(sta_re+"(под)*\\s*ипотек(\\w*)"+end_re);
            Matcher m = p.matcher(query);
            if (m.find()){
                r.filterList.add(new FilterObject("mortgages", 1));
                query = m.replaceFirst("");
            }
        }
        //ownerPrice
        {
            boolean matched;
            Integer exactPrice = null;
            Integer price1 = null;
            Integer price2 = null;

            Pattern p;
            Matcher m;

            do {
                matched = false;

                String float_re = "\\d+(?:[,.]\\d+)?";
                String rub_re = "р(?:\\.|(?:уб(?:\\.|лей)?)?)?";
                String ths_re = "т(?:\\.|(?:ыс(?:\\.|яч)?)?)?";
                String mln_re = "(?:(?:млн\\.?)|(?:миллион\\w*))";
                String allv_re = "((?:" + rub_re + ")|(?:" + ths_re + "\\s*" + rub_re + ")|(?:" + mln_re + "\\s*(?:" + rub_re + ")?)?)?";
                // Range
                p = Pattern.compile(
                        sta_re + "(?:(?:от|с)\\s+)?(" + float_re + ")\\s*" + allv_re + "\\s*(?:до|по|\\-)\\s*(" + float_re + ")\\s*" + allv_re + end_re);
                m = p.matcher(query);
                if (m.find()) {
                    String ss = m.group(2);
                    String ss1 = m.group(4);
                    if(ss1 == null || ss1.equals(""))
                        break;
                    if(m.group(2) == null || m.group(2).equals(""))
                        ss = ss1;
                    price1 = Integer.parseInt(m.group(1));
                    price2 = Integer.parseInt(m.group(3));

                    if (regExTest("^" + rub_re + "$", ss)) {
                        price1 = price1 / 1000;
                    } else if (regExTest("^" + mln_re + "\\s*(?:" + rub_re + ")?$", ss)) {
                        price1 = price1 * 1000;
                    }

                    if (regExTest("^" + rub_re + "$", ss1)) {
                        price2 = price2 / 1000;
                    } else if (regExTest("^" + mln_re + "\\s*(?:" + rub_re + ")?$", ss1)) {
                        price2 = price2 * 1000;
                    }

                    query = m.replaceFirst("");
                }
                // Single value
                else {
                    p = Pattern.compile(sta_re + "(?:(" + tofrom_re + ")\\s+)?(" + float_re + ")\\s*((?:" + rub_re + ")|(?:" + ths_re + "\\s*" + rub_re + ")|(?:" + mln_re + "\\s*(?:" + rub_re + ")?))" + end_re);
                    m = p.matcher(query);
                    if (m.find()) {
                        String prefix = m.group(1);
                        String ss = m.group(3);
                        Integer price = Integer.parseInt(m.group(2));

                        if (regExTest("^" + rub_re + "$", ss)) {
                            price = price / 1000;
                        } else if (regExTest("^" + mln_re + "\\s*(?:" + rub_re + ")?$", ss)) {
                            price = price * 1000;
                        } else {
                            price = price;
                        }
                        if (prefix != null) {
                            if (prefix.matches("от") || prefix.matches("с")) {
                                price1 = price;
                            } else if (prefix.matches("до") || prefix.matches("по")) {
                                price2 = price;
                            }
                        } else {
                            exactPrice = price;
                        }
                        //matched = true;
                        query = m.replaceFirst("");
                    }
                }
            } while (matched == true);

            if (price1 != null || price2 != null) {
                r.filterList.add(new FilterObject("ownerPrice", price1, price2));
            } else if (exactPrice != null) {
                r.filterList.add(new FilterObject("ownerPrice", exactPrice));
            }
        }

        // Rooms count
        {
            boolean matched = false;
            Integer rooms_count = null;
            Integer rooms_count1 = null;
            Integer rooms_count2 = null;

            do {
                matched = false;
                String rm_re = "к(?:\\.|(?:омн(?:\\.|ат\\w*|атная\\w*)?)?)?";
                // range
                Pattern p = Pattern.compile(
                    sta_re + "(?:(?:от|с|со)\\s+)?(\\d)\\s*"+rm_re+"\\s*(?:до|по|\\-)\\s*(\\d)\\s*"+ rm_re + end_re);
                Matcher m = p.matcher(query);

                if(m.find()){
                    rooms_count1 = Integer.parseInt(m.group(1));
                    rooms_count2 = Integer.parseInt(m.group(2));
                    query = m.replaceFirst("");
                }else {
                    //single
                    p = Pattern.compile(
                            sta_re + "(\\d)(?:\\-?х\\s)?\\s*"+ rm_re + end_re);
                    m = p.matcher(query);
                    if (m.find()) {
                        rooms_count = Integer.parseInt(m.group(1));

                        query = m.replaceFirst("");

                    } else {
                        // special case
                        p = Pattern.compile(
                                sta_re + "(одн[оа]|двух|трех|четырех|пяти|шести|семи|восьми|девяти)\\s*комн(?:\\.|(?:ат\\w*|атная\\w*)?)?" + end_re);
                        m = p.matcher(query);
                        if (m.find()) {
                            String v = m.group(1);

                            if (v.matches("одно")) rooms_count = 1;
                            if (v.matches("двух")) rooms_count = 2;
                            if (v.matches("трех")) rooms_count = 3;
                            if (v.matches("четырех")) rooms_count = 4;
                            if (v.matches("пяти")) rooms_count = 5;
                            if (v.matches("шести")) rooms_count = 6;
                            if (v.matches("семи")) rooms_count = 7;
                            if (v.matches("восьми")) rooms_count = 8;
                            if (v.matches("девяти")) rooms_count = 9;

                            query = m.replaceFirst("");
                        }
                    }
                }
            } while (matched);

            if (rooms_count1 != null || rooms_count2 != null) {
                r.filterList.add(new FilterObject("roomsCount", rooms_count1, rooms_count2));
            } else if (rooms_count != null) {
                r.filterList.add(new FilterObject("roomsCount", rooms_count));
            };
        }

        // Floor
        {
            boolean matched = false;
            Integer exactFloor = null;
            Integer floor1 = null;
            Integer floor2 = null;
            do {
                matched = false;

                String flr_re = "э(?:\\.|(?:т(?:\\.|аж\\w*)?)?)?";

                // Range
                Pattern p = Pattern.compile(
                        sta_re + "(?:(?:от|с|со)\\s+)?(\\d{1,2})\\s*"+flr_re+"\\s*(?:до|по|\\-)\\s*(\\d{1,2})\\s*" + flr_re + end_re);
                Matcher m = p.matcher(query);
                if (m.find()) {
                    floor1 = Integer.parseInt(m.group(1));
                    floor2 = Integer.parseInt(m.group(2));

                    query = m.replaceFirst("");
                } else {
                    // Single value
                    p = Pattern.compile(
                            sta_re + "(?:(" + tofrom_re + ")\\s+)?(\\d{1,2})\\s*" + flr_re + end_re);
                    m = p.matcher(query);
                    if (m.find()) {

                        String prefix = m.group(1);
                        if (prefix != null) {
                            if (prefix.matches("до") || prefix.matches("по")){
                                floor2 = Integer.parseInt(m.group(2));
                            } else if(prefix.matches("от") || prefix.matches("с")) {
                                floor1 = Integer.parseInt(m.group(2));
                            }
                        } else{
                            exactFloor = Integer.parseInt(m.group(2));
                        }

                        query = m.replaceFirst("");

                    }
                }
            } while (matched);

            if (floor1 != null || floor2 != null) {
                r.filterList.add(new FilterObject("floor", floor1, floor2));
            } else if (exactFloor != null) {
                r.filterList.add(new FilterObject("floor", exactFloor));
            }
        }

        // Square
        {
            boolean matched = false;
            Integer exactSquare = null;
            Integer square1 = null;
            Integer square2 = null;
            do {
                matched = false;

                String sqr_re = "(?:кв(?:\\.|адратн\\w*)?)?\\s*м(?:\\.|2|етр\\w*)?";

                // Range
                Pattern p = Pattern.compile(
                        sta_re + "(?:(?:от|с)\\s+)?(\\d+)\\s*"+sqr_re+"\\s*(?:до|по|\\-)\\s*(\\d+)\\s*" + sqr_re + end_re);
                Matcher m = p.matcher(query);
                if (m.find()) {
                    square1 = Integer.parseInt(m.group(1));
                    square2 = Integer.parseInt(m.group(2));

                    query = m.replaceFirst("");
                } else {
                    // Single value

                    p = Pattern.compile(
                            sta_re + "(?:(" + tofrom_re + ")\\s+)?(\\d+)\\s*" + sqr_re + end_re);
                    m = p.matcher(query);
                    if (m.find()) {
                        String prefix = m.group(1);

                        if (prefix != null) {
                            if (prefix.matches("до") || prefix.matches("по")) {
                                square2 = Integer.parseInt(m.group(2));
                            } else if(prefix.matches("от") || prefix.matches("с")){
                                square1 = Integer.parseInt(m.group(2));
                            }
                        } else {
                            exactSquare = Integer.parseInt(m.group(2));
                        }

                        query = m.replaceFirst("");
                    }
                }
            } while (matched);

            if (square1 != null || square2 != null) {
                r.filterList.add(new FilterObject("squareTotal", square1, square2));
            } else if (exactSquare != null) {
                r.filterList.add(new FilterObject("squareTotal", exactSquare));
            }
        }

        ParseResult phones_res = parseContacts(query);
        if(phones_res.filterList.size() > 0)
            r.filterList.add(phones_res.filterList.get(0));
        query = phones_res.query;
        // phones
        /*{
            boolean matched = false;
            List<String> res = new ArrayList<>();
            do {
                matched = false;
                Pattern p = Pattern.compile(
                            sta_re + "\\+*\\s*((?:7|8)*(?:\\(|\\s)*\\d{3,4}(?:\\)|\\s)*\\d{2,3}(?:\\s|\\-)*\\d{2}(?:\\s|\\-)*\\d{2})" + end_re);
                Matcher m = p.matcher(query);
                if (m.find()) {
                    String phone = PhoneBlock.normalisePhone(m.group(1));
                    res.add(phone);
                    query = m.replaceAll("");
                    matched = true;
                }
            } while (matched);

            if (res.size() > 0) {
                r.filterList.add(new FilterObject("phones", res));
            }
        }*/

        r.query = query;

        return r;
    }

    public static ParseResult parseContacts (String query) {
        String sta_re = "(?:^|\\s+|,\\s*)";
        String end_re = "(?:\\s+|,|$)";
        boolean matched = false;
        List<String> res = new ArrayList<>();
        ParseResult r = new ParseResult();

        do {
            matched = false;
            Pattern p = Pattern.compile(
                    sta_re + "\\+*\\s*((?:7|8)*(?:\\(|\\s)*\\d{3,4}(?:\\)|\\s)*\\d{2,3}(?:\\s|\\-)*\\d{2}(?:\\s|\\-)*\\d{2})" + end_re);
            Matcher m = p.matcher(query);
            if (m.find()) {
                String phone = PhoneBlock.normalisePhone(m.group(1));
                res.add(phone);
                query = m.replaceAll("");
                matched = true;
            }
        } while (matched);

        if (res.size() > 0) {
            r.filterList.add(new FilterObject("phones", res));
        }

        res = new ArrayList<>();

        do {
            matched = false;
            Pattern p = Pattern.compile(
                    sta_re + "(\\S+@\\S+)" + end_re);
            Matcher m = p.matcher(query);
            if (m.find()) {
                String mail = EmailBlock.normaliseMail(m.group(1)).replaceAll("@", "");
                res.add(mail);
                query = m.replaceAll("");
                matched = true;
            }
        } while (matched);

        if (res.size() > 0) {
            r.filterList.add(new FilterObject("emails", res));
        }

        query = query.toLowerCase().trim();
        query = query.replace("й","и");
        query = query.replace("ё","е");
        query = query.replace("ъ","ь");
        
        r.query = query;
        return r;
    }

    public static Map<String, String> process (String query) {

        Map<String, String> result = new HashMap<String, String>();
        String req = null; //строка запроса
        String excl = null; //строка исключения
        String near = null; //строка близости
        Pattern p = Pattern.compile("(.+|)(кроме |не )(.+)");
        Matcher m = p.matcher(query);
        if (m.find()) {
            req = m.group(1);
            excl = m.group(3);
        } else {
            req = query;
        }

        Pattern.compile("(рядом |рядом с)(.+)");
        if (m.find()) {
            near = m.group(2);
        }

        result.put("req", req);
        result.put("excl", excl);
        result.put("near", near);
        return result;
    }
}
