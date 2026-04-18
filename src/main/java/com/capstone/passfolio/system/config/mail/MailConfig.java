package com.capstone.passfolio.system.config.mail;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

@Configuration
@RequiredArgsConstructor
public class MailConfig {
    private static final String MAIL_SMTP_AUTH = "mail.smtp.auth";
    private static final String MAIL_DEBUG = "mail.smtp.debug";
    private static final String MAIL_CONNECTION_TIMEOUT = "mail.smtp.connectiontimeout";
    private static final String MAIL_SMTP_STARTTLS_ENABLE = "mail.smtp.starttls.enable";
    private static final String MAIL_SMTP_STARTTLS_REQUIRED = "mail.smtp.starttls.required";
    private static final String MAIL_SMTP_WRITE_TIMEOUT = "mail.smtp.writetimeout";
    private static final String MAIL_SMTP_TIMEOUT = "mail.smtp.timeout";

    @Value("${spring.mail.host}") private String host;
    @Value("${spring.mail.username}") private String username;
    @Value("${spring.mail.password}") private String password;
    @Value("${spring.mail.port}") private int port;
    @Value("${spring.mail.properties.mail.smtp.auth}") private boolean auth;
    @Value("${spring.mail.properties.mail.smtp.debug}") private boolean debug;
    @Value("${spring.mail.properties.mail.smtp.connectiontimeout}") private int connectionTimeout;
    @Value("${spring.mail.properties.mail.smtp.starttls.enable}") private boolean starttlsEnable;
    @Value("${spring.mail.properties.mail.smtp.starttls.required:false}") private boolean starttlsRequired;
    @Value("${spring.mail.properties.mail.smtp.writetimeout}") private int writetimeout;
    @Value("${spring.mail.properties.mail.smtp.timeout}") private int timeout;

    @Bean
    public JavaMailSender javaMailSender() {
        JavaMailSenderImpl javaMailSender = new JavaMailSenderImpl();
        javaMailSender.setHost(host);
        javaMailSender.setUsername(username);
        javaMailSender.setPassword(password);
        javaMailSender.setPort(port);

        Properties properties = javaMailSender.getJavaMailProperties();
        properties.put(MAIL_SMTP_AUTH, auth);
        properties.put(MAIL_DEBUG, debug);
        properties.put(MAIL_CONNECTION_TIMEOUT, connectionTimeout);
        properties.put(MAIL_SMTP_STARTTLS_ENABLE, starttlsEnable);
        properties.put(MAIL_SMTP_STARTTLS_REQUIRED, starttlsRequired);
        properties.put(MAIL_SMTP_WRITE_TIMEOUT, writetimeout);
        properties.put(MAIL_SMTP_TIMEOUT, timeout);

        javaMailSender.setDefaultEncoding("UTF-8");
        javaMailSender.setJavaMailProperties(properties);

        return javaMailSender;
    }
}
