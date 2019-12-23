package utils;

import configuration.AppConfig;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Type;
import java.net.URL;
import java.time.Instant;
import java.util.List;
import java.util.Map;


public class CommonUtils {

    static Logger logger = LoggerFactory.getLogger(CommonUtils.class);


    public static String strNotNull(String str) {
        return str == null ? "" : str;
    }

    public static Boolean strNotNull(Boolean val) {
        return val == null ? false : val;
    }

    public static String replaceSymb(String str){
        if(str == null || str.equals("")){
            str = "";
        } else {
            str = str.replace("й","и").replace("ё","е").replace("ъ","ь").toLowerCase().replaceAll("  ", " ").replaceAll("\\.", "\\. ");
        }
        return str;
    }

    public static long getUnixTimestamp() {
        return System.currentTimeMillis() / 1000L;
    }

    public static long getSystemTimestamp() {
        return System.currentTimeMillis();
    }

    public static Map<String, String> JsonToMap(String json) {
        Gson gson = new Gson();
        Type stringStringMap = new TypeToken<Map<String, String>>(){}.getType();
        Map<String, String> map = gson.fromJson(json, stringStringMap);
        return map;
    }

    public static Map<String, Object> JsonToObjMap(String json) {
        Gson gson = new Gson();
        Type stringStringMap = new TypeToken<Map<String, Object>>(){}.getType();
        Map<String, Object> map = gson.fromJson(json, stringStringMap);
        return map;
    }

    public static List<String> JsonToList(String json) {
        Gson gson = new Gson();
        Type stringList = new TypeToken<List<String>>(){}.getType();
        List<String> list = gson.fromJson(json, stringList);
        return list;
    }

    public static String downloadPhoto(String photoUrl) throws Exception {
        BufferedImage image = null;

        URL url = new URL(photoUrl);
        image = ImageIO.read(url);

        Instant now = Instant.now();
        File outputfile = new File(AppConfig.PHOTO_STORAGE_PATH + Long.toString(now.toEpochMilli()) + ".png");
        ImageIO.write(image, "png", outputfile);

        return outputfile.getName();
    }
}
