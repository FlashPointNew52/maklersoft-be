package resource;

import auxclass.ErrorMsg;
import auxclass.UploadFile;
import com.google.gson.*;
import configuration.AppConfig;
import entity.User;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.UploadService;

import java.io.*;

import static spark.Spark.post;
import static spark.Spark.options;
import javax.servlet.MultipartConfigElement;
import javax.servlet.http.Part;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class UploadResource {
    Logger logger = LoggerFactory.getLogger(UploadResource.class);
    Gson gson = new GsonBuilder().create();

    public UploadResource(UploadService uploadService) {
        setupEndpoints();
    }

    private void setupEndpoints() {
        options(AppConfig.API_CONTEXT + "/upload/file", (request, response) -> {
            response.header("Access-Control-Request-Method", "*");
            return "OK";
        });

        post(AppConfig.API_CONTEXT + "/upload/file", "multipart/form-data", (request, response) -> {
            Map<String, Object> result = new HashMap<>();
            String location = "files";
            long maxFileSize = 100000000;       // the maximum size allowed for uploaded files
            long maxRequestSize = 100000000;    // the maximum size allowed for multipart/form-data requests
            int fileSizeThreshold = 1024;       // the size threshold after which files will be written to disk

            User us = request.session().attribute("user");
            Long accountId = us.getAccountId();
            Long userId = us.getId();

            String filePath = AppConfig.TEMP_FILE_LOC_PATH + accountId + "/" + userId;
            if(new File(filePath).mkdirs()){} //создадим необходимкю дирректорию

            MultipartConfigElement multipartConfigElement = new MultipartConfigElement(location, maxFileSize, maxRequestSize, fileSizeThreshold);
            request.raw().setAttribute("org.eclipse.jetty.multipartConfig", multipartConfigElement);
            Collection<Part> col = request.raw().getParts();
            ArrayList<UploadFile> files = new ArrayList<>();
            for (Part part : col) {
                UploadFile fl = new UploadFile(part.getSubmittedFileName(), userId, true);

                Path out = Paths.get(filePath + "/" + fl.fullName);
                fl.href = "http://storage.maklersoft.com/temp/" + accountId + "/" + userId + "/" + fl.fullName;
                File delFile = new File(filePath + "/" + fl.fullName);
                if (delFile.delete()) { } //Удалим файл
                try (final InputStream in = part.getInputStream()) {
                    Files.copy(in, out);
                    part.delete();
                    files.add(fl);
                } catch (Exception exp) {
                    //System.out.println(exp);
                }
            }

            result.put("response", "ok");
            result.put("files", files);

            return result;
        }, gson::toJson);

        post(AppConfig.API_CONTEXT + "/upload/delete", "application/json", (request, response) -> {
            Map<String, Object> result = new HashMap<>();
            JsonObject jsonObject = new JsonParser().parse(request.body()).getAsJsonObject();
            String deleteFile = jsonObject.get("fileName").toString().replace(AppConfig.FILE_STORAGE_URL,AppConfig.FILE_STORAGE_PATH);

            try{
                new File(deleteFile).delete();
                response.status(200);
                result.put("response", "ok");
            } catch(Exception exp){
                ErrorMsg err = new ErrorMsg("001: System error | " + ExceptionUtils.getStackTrace(exp), "Ошибка при удалении файла!",
                        "UploadFileError", 0);
                response.status(400);
                return gson.toJson(err);
            }

            return result;
        }, gson::toJson);
    }
}
