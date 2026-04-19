package com.capstone.passfolio.domain.auth.service;

import com.capstone.passfolio.domain.auth.dto.EmailPurpose;
import com.capstone.passfolio.domain.auth.repository.EmailRedisRepository;
import com.capstone.passfolio.system.exception.model.ErrorCode;
import com.capstone.passfolio.system.exception.model.RestException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.io.UnsupportedEncodingException;
import java.security.SecureRandom;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {
    private final JavaMailSender javaMailSender;
    private final SpringTemplateEngine templateEngine;
    private final EmailRedisRepository emailRedisRepository;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String EMAIL_SUBJECT = " Passfolio 이메일 인증";

    @Value("${spring.mail.username}") private String mailUsername;
    @Value("${spring.mail.group}") private String mailGroup;

    public void sendEmail(String receiverEmail) {
        String normalizedEmail = emailNormalize(receiverEmail);
        try {
            log.info("🟡 Trying to send Email to {}", receiverEmail);

            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper mimeMessageHelper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            mimeMessageHelper.setFrom(new InternetAddress(mailUsername, mailGroup, "UTF-8"));
            mimeMessageHelper.setTo(normalizedEmail);

            String verificationCode = generateVerificationCode();

            mimeMessageHelper.setSubject(EMAIL_SUBJECT);
            mimeMessageHelper.setText(setContext(verificationCode), true);

            ClassPathResource logoResource = new ClassPathResource("templates/Passfolio_Main_logo.png");
            mimeMessageHelper.addInline("passfolioLogo", logoResource, "image/png");

            javaMailSender.send(mimeMessage);

            emailRedisRepository.saveVerificationCode(normalizedEmail, verificationCode);

            log.info("🟢 Success to Send Email to {}", receiverEmail);
        } catch (MessagingException e) {
            log.error("🔴 Failed to Send Email to {}: {}", receiverEmail, e.getMessage(), e);
            throw new RuntimeException("이메일 전송 실패: " + e.getMessage(), e);
        } catch (UnsupportedEncodingException e) {
            log.error("🔴 Failed to Send Email to {}: {}", receiverEmail, e.getMessage(), e);
            throw new RuntimeException("인코딩 실패: " + e.getMessage(), e);
        } catch (RedisConnectionFailureException e) {
            log.error("🔴 Redis 연결 실패 (이메일 발송 중): {}", e.getMessage(), e);
            throw new RestException(ErrorCode.REDIS_CONNECTION_FAILED);
        } catch (DataAccessException e) {
            log.error("🔴 Redis 데이터 접근 오류 (이메일 발송 중): {}", e.getMessage(), e);
            throw new RestException(ErrorCode.REDIS_ERROR);
        } catch (MailException e) {
            log.error("🔴 메일 서버 연결 실패 (이메일 발송 중): {}", e.getMessage(), e);
            throw new RestException(ErrorCode.MAIL_CONNECTION_FAILED);
        }
    }

    public void verifyEmailCode(String email, String code, EmailPurpose purpose) {
        try {
            log.info("🟡 Trying to verify Email to {}", email);
            String normalizedEmail = emailNormalize(email);
            String savedCode = emailRedisRepository.getVerificationCode(normalizedEmail);

            validateSavedCode(savedCode, code);

            emailRedisRepository.deleteVerificationCode(normalizedEmail);
            setFlagByPurpose(purpose, normalizedEmail);
        } catch (RedisConnectionFailureException e) {
            log.error("🔴 Redis 연결 실패 (인증 코드 검증 중): {}", e.getMessage(), e);
            throw new RestException(ErrorCode.REDIS_CONNECTION_FAILED);
        } catch (DataAccessException e) {
            log.error("🔴 Redis 데이터 접근 오류 (인증 코드 검증 중): {}", e.getMessage(), e);
            throw new RestException(ErrorCode.REDIS_ERROR);
        }
    }

    public boolean hasVerifiedForSignup(String email) {
        try {
            return emailRedisRepository.hasVerifiedForSignup(emailNormalize(email));
        } catch (RedisConnectionFailureException e) {
            log.error("🔴 Redis 연결 실패 (플래그 확인 중): {}", e.getMessage(), e);
            throw new RestException(ErrorCode.REDIS_CONNECTION_FAILED);
        } catch (DataAccessException e) {
            log.error("🔴 Redis 데이터 접근 오류 (플래그 확인 중): {}", e.getMessage(), e);
            throw new RestException(ErrorCode.REDIS_ERROR);
        }
    }

    public void clearVerifiedForSignup(String email) {
        try {
            emailRedisRepository.clearVerifiedForSignup(emailNormalize(email));
        } catch (RedisConnectionFailureException e) {
            log.error("🔴 Redis 연결 실패 (플래그 삭제 중): {}", e.getMessage(), e);
            throw new RestException(ErrorCode.REDIS_CONNECTION_FAILED);
        } catch (DataAccessException e) {
            log.error("🔴 Redis 데이터 접근 오류 (플래그 삭제 중): {}", e.getMessage(), e);
            throw new RestException(ErrorCode.REDIS_ERROR);
        }
    }

    private String generateVerificationCode() {
        int code = 100000 + SECURE_RANDOM.nextInt(900000);
        return String.valueOf(code);
    }

    private String setContext(String verificationCode) {
        Context context = new Context();
        context.setVariable("date", LocalDateTime.now());
        context.setVariable("verificationCode", verificationCode);
        return templateEngine.process("emailVerification", context);
    }

    private void validateSavedCode(String savedCode, String verificationCode) {
        if (savedCode == null) {
            throw new RestException(ErrorCode.AUTH_EMAIL_CODE_INVALID);
        }
        if (!savedCode.equals(verificationCode)) {
            throw new RestException(ErrorCode.AUTH_EMAIL_CODE_NOT_MATCHED);
        }

        log.info("🟢 Successful Verification Email Code {} : {}", savedCode, verificationCode);
    }

    private String emailNormalize(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private void setFlagByPurpose(EmailPurpose purpose, String normalizedEmail) {
        if (purpose == EmailPurpose.SIGNUP) {
            emailRedisRepository.markVerifiedForSignup(normalizedEmail);
        } else if (purpose == EmailPurpose.PASSWORD_RESET) {
            emailRedisRepository.markVerifiedForPasswordReset(normalizedEmail);
        }
    }
}
