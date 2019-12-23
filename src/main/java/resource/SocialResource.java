package resource;

import auxclass.VK;
import com.google.gson.Gson;
import com.google.gson.JsonParser;
import configuration.AppConfig;
import entity.Account;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;

import static spark.Spark.get;
import static spark.Spark.post;

public class SocialResource {
    Logger logger = LoggerFactory.getLogger(OfferResource.class);
    Gson gson = new Gson();


    public SocialResource() {
        setupEndpoints();
    }

    private void setupEndpoints() {
        post("/social/publish", (request, response) -> {
            HashMap<String, Object> result = new HashMap<>();

            String body = request.body();
            VK vk = gson.fromJson(request.body(), VK.class);

            URL url1 = new URL(vk.getUrl());
            URLConnection con1 = url1.openConnection();
            HttpURLConnection uc = (HttpURLConnection) con1;
            uc.setRequestMethod("POST"); // PUT is another valid option
            uc.setDoOutput(true);
            uc.setDoInput(true);

            URL url = new URL(vk.getPhoto());
            BufferedImage img = ImageIO.read(url);
            File file = new File("downloaded" + vk.getIndex() + ".jpg");
            ImageIO.write(img, "jpg", file);

            FileBody fileBody = new FileBody(file);
            MultipartEntity multipartEntity = new MultipartEntity(HttpMultipartMode.STRICT);
            multipartEntity.addPart("file" + vk.getIndex(), fileBody);

            uc.setRequestProperty("Content-Type", multipartEntity.getContentType().getValue());
            OutputStream out = uc.getOutputStream();
            try {
                multipartEntity.writeTo(out);
            } finally {
                out.close();
            }
            uc.connect();
            int status = uc.getResponseCode();
            System.out.println("status: " + status);

            if (status == 201 || status == 200) {
                try {
                    String jsonStr;
                    BufferedReader br = new BufferedReader(new InputStreamReader(uc.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line).append("\n");
                    }
                    br.close();
                    jsonStr = sb.toString();
                    System.out.println(jsonStr);
                    result.put("vk_answer", jsonStr);
                    result.put("server", new JsonParser().parse(jsonStr).getAsJsonObject().get("server"));
                    result.put("photo", new JsonParser().parse(jsonStr).getAsJsonObject().get("photo"));
                    result.put("hash", new JsonParser().parse(jsonStr).getAsJsonObject().get("hash"));
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                    result.put("result", "failed save");
                }
            } else {
                System.out.println("failed " + status);
            }
            result.put("result", status);

            return result;

        }, gson::toJson);
    }
}

