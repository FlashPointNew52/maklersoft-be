package utils;

import java.util.Properties;
import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.swing.text.html.HTMLDocument;

public class Email {
    private String username = "info@rplusmgmt.com";//"info@rplusmgmt.com";
    private String password = "DU21071969";//"ckj;ysqgfhjkm";
    private Properties properties;

    String from = "info@rplusmgmt.com";       // receiver email
    String host = "smtp.yandex.ru";            // mail server host

    public Email() {
        properties = new Properties();
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.starttls.enable", "true");
        properties.put("mail.smtp.host", host);
        properties.put("mail.smtp.port", "587");


    }

    public void SendMail(String to, String subject, String text){
        Session session = Session.getInstance(properties, new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });
        try{
            MimeMessage message = new MimeMessage(session); // email message

            message.setFrom(new InternetAddress(from)); // setting header fields

            message.addRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            message.setSubject(subject); // subject line

            message.setText(text);

            Transport.send(message);
        } catch(Exception mex){
            mex.printStackTrace();
        }
    }

    public void SendHtmlMail(String to, String cc, String subject, String htmlMessage){
        Session session = Session.getInstance(properties, new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });
        try{
            MimeMessage message = new MimeMessage(session); // email message

            message.setFrom(new InternetAddress(from)); // setting header fields

            message.addRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            message.addRecipients(Message.RecipientType.CC, InternetAddress.parse(cc));
            message.setSubject(subject); // subject line

            message.setContent(htmlMessage, "text/html; charset=utf-8");

            Transport.send(message);
        } catch(Exception mex){
            mex.printStackTrace();
        }
    }

}
