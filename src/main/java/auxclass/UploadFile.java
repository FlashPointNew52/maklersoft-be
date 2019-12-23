package auxclass;


import configuration.AppConfig;
import javafx.scene.transform.Scale;
import service.CommentService;
import utils.CommonUtils;

import javax.imageio.ImageIO;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.imgscalr.Scalr;

public class UploadFile {
    public String name;
    public String fullName;
    public String ext;
    public String href;
    public Long addDate;
    public Long userId;
    public String userName;
    public int type;
    public Boolean isTemp;
    public String hash;

    Logger logger = LoggerFactory.getLogger(UploadFile.class);

    public UploadFile(String name, Long userId, Boolean isTemp){
        this.fullName = name.replaceAll("(\\s|\\(|\\)|,)", "_");
        this.ext = this.fullName.substring(this.fullName.lastIndexOf(".") + 1);
        this.name = this.fullName.replace("." + this.ext, "");
        this.addDate = CommonUtils.getUnixTimestamp();
        this.userId = userId;
        this.isTemp = isTemp;

        if(this.ext.matches("jpg|jpeg|gif|png|bmp")) this.type = 0;
        else if(this.ext.matches("doc|docx|pdf|xls|xlsx|txt|rtf|odt")) this.type = 1;
        else this.type = 3;
    }

    public static ArrayList moveFromTemp(ArrayList<UploadFile> files, String subpath ){
        files.forEach((file) -> {
            if(file.isTemp){
                String newLocation = AppConfig.FILE_STORAGE_PATH + subpath;
                //создадим необходимкю дирректорию
                if(new File(newLocation).mkdirs()){}
                //Проверим на существование файла, если есть, то меням имя файла
                while (true){
                    if(new File(newLocation + "/" + file.fullName).exists()){
                        file.name = file.name + "-1";
                        file.fullName = file.name + "." + file.ext;
                    } else{
                        break;
                    }
                }
                String newPath = newLocation + "/" + file.fullName;
                //Получим путь к файлу из ссылки
                File fToMove = new File(file.href.replace(AppConfig.FILE_STORAGE_URL, AppConfig.FILE_STORAGE_PATH));
                //Переместим файл из временногог каталога в постоянный
                if (fToMove.renameTo(new File(newPath))) {
                    if (fToMove.delete()) { }//Удалим временный файл
                }


                file.href = newPath.replace(AppConfig.FILE_STORAGE_PATH, AppConfig.FILE_STORAGE_URL);
                file.isTemp = false;
            }
        });
        return files;
    }

    public static String moveFromTemp(String path, String subpath, boolean preClean){
        if(path != null && path.indexOf(AppConfig.FILE_STORAGE_URL + "temp/") != -1){
            String newLocation = AppConfig.FILE_STORAGE_PATH + subpath;
            String fileName = path.substring(path.lastIndexOf('/'));
            String newPath = newLocation + fileName;
            //Если нужно, то чистим содержимое каталога
            if(preClean){ delete(new File(newLocation));}
            //создадим необходимкю дирректорию
            if(new File(newLocation).mkdirs()){}
            //удалим если файл с таким именем уже есть
            if(new File(newPath).delete()) {}
            //Получим путь к файлу из ссылки
            File fToMove = new File(path.replace(AppConfig.FILE_STORAGE_URL, AppConfig.FILE_STORAGE_PATH));
            //Переместим файл из временногог каталога в постоянный
            fToMove.renameTo(new File(newPath));
            //Удалим временный файл
            if (fToMove.delete()) { }
            return newPath.replace(AppConfig.FILE_STORAGE_PATH, AppConfig.FILE_STORAGE_URL);
        }
        else return path;
    }

    public String minimazePhoto(String path, String subpath, int wMax, int hMax) throws IOException {
        BufferedImage image = ImageIO.read(new File(path.replace(AppConfig.FILE_STORAGE_URL, AppConfig.FILE_STORAGE_PATH)));
        int wNew=0, hNew = 0;
        boolean needMinimaze = false;
        if(image.getWidth() >= image.getHeight()){
            if(image.getWidth() > wMax){
                wNew = wMax;
                hNew = image.getHeight() * wMax/image.getWidth();
                needMinimaze = true;
            }
        } else{
            if(image.getHeight() > hMax){
                hNew = hMax;
                wNew = image.getWidth() * hMax/image.getHeight();
                needMinimaze = true;
            }
        }
        if(needMinimaze){
            this.logger.info("need minimaze");
            String newLocation = AppConfig.FILE_STORAGE_PATH + subpath;
            if(new File(newLocation).mkdirs()){}
            String fileName = path.substring(path.lastIndexOf('/'));
            String newPath = newLocation + fileName;


            BufferedImage scaledImg = Scalr.resize(image, Scalr.Method.QUALITY,
                    wNew, hNew, Scalr.OP_ANTIALIAS);

            ImageIO.write(scaledImg, "JPG", new File(newPath));
            return newPath.replace(AppConfig.FILE_STORAGE_PATH, AppConfig.FILE_STORAGE_URL);
        }
        else {
            this.logger.info("no minimaze");
            return path;
        }
    }

    public void delete(){
        File delFile = new File(this.href.replace(AppConfig.FILE_STORAGE_URL, AppConfig.FILE_STORAGE_PATH));
        delFile.delete();
    }

    public static void deleteDirectory(String path) {
        File direct = new File(path);
        if(!direct.exists()) return;
        if(direct.isDirectory()) {
            for(File f : direct.listFiles())
                delete(f);
            direct.delete();
        }

    }

    private static void delete(final File file) {
        if(file.isDirectory()){
            String[] files = file.list();
            if((null == files) || (files.length == 0)){
                file.delete();
            } else {
                for(final String filename: files){
                    delete(new File(file.getAbsolutePath() + File.separator + filename));
                }
                file.delete();
            }
        } else {
            file.delete();
        }
    }

    public static void deleteFromUrl(String url){
        new File(url.replace(AppConfig.FILE_STORAGE_URL, AppConfig.FILE_STORAGE_PATH)).delete();
    }

}
