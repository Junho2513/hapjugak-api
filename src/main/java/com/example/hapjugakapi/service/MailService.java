package com.example.hapjugakapi.service;

import com.example.hapjugakapi.exception.MailSendException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;
import software.amazon.awssdk.services.sesv2.model.SesV2Exception;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 메일을 보낸다. 보낼 내용을 만드는 곳이지, 누구에게 왜 보낼지를 정하는 곳은 아니다.
 *
 * 왜 Spring 의 JavaMailSender(SMTP)가 아니라 SES API 인가
 * - SMTP 를 쓰려면 서버에 SMTP 사용자 이름과 비밀번호를 둬야 한다.
 *   API 를 쓰면 IAM 역할로 끝나서 서버에 자격 증명 파일이 남지 않는다.
 * - 25번 포트는 AWS 가 기본으로 막아둔다. 587 로 우회할 수 있지만 그럴 이유가 없다.
 *
 * 보내는 쪽은 HTML 과 일반 텍스트를 함께 싣는다.
 * 텍스트만 읽는 메일 앱이 아직 있고, 둘 다 있는 메일이 스팸 점수도 낫다.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    /** 발신자 표시 이름. 받는 사람 목록에 "합주각"으로 보인다. */
    private static final String FROM_NAME = "합주각";

    /** application.properties 는 한글 값이 깨질 수 있어서 표시 이름은 코드에 둔다. */
    private static final String SITE_URL = "https://hapjugak.com";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final SesV2Client ses;
    private final String from;
    private final String logoUrl;

    /** 템플릿은 띄울 때 한 번만 읽는다. 메일 한 통마다 파일을 여는 건 낭비다. */
    private final String layout;

    public MailService(SesV2Client ses,
                       @Value("${app.mail.from-address}") String fromAddress,
                       @Value("${app.mail.logo-url}") String logoUrl) {
        this.ses = ses;
        this.from = FROM_NAME + " <" + fromAddress + ">";
        this.logoUrl = logoUrl;
        this.layout = readLayout();
    }

    /** 가입할 때 이메일 주소가 본인 것인지 확인하는 메일. */
    public void sendVerificationCode(String to, String code, int minutes) {
        sendCodeMail(
                to,
                "[합주각] 이메일 인증번호 " + code,
                "이메일 주소를 확인해주세요",
                "합주각 가입을 마치려면 아래 인증번호를 입력해주세요.",
                code,
                minutes,
                "본인이 요청하지 않았다면 이 메일을 무시하셔도 됩니다. 누군가 이 주소를 잘못 입력했을 수 있습니다."
        );
    }

    /** 비밀번호를 잊었을 때 보내는 메일. */
    public void sendPasswordResetCode(String to, String code, int minutes) {
        sendCodeMail(
                to,
                "[합주각] 비밀번호 재설정 인증번호 " + code,
                "비밀번호를 다시 설정합니다",
                "아래 인증번호를 입력하면 새 비밀번호를 정할 수 있습니다.",
                code,
                minutes,
                "본인이 요청하지 않았다면 이 메일을 무시하셔도 됩니다. 비밀번호는 그대로 유지됩니다."
        );
    }

    /**
     * 발송 설정이 살아 있는지 확인하는 메일.
     *
     * 받는 주소를 입력받지 않는다. 요청한 관리자 본인에게만 간다.
     * 주소를 받게 만들면 관리자 토큰이 새는 순간 아무 주소로나 메일을 뿌릴 수 있다.
     */
    public void sendTest(String to) {
        sendCodeMail(
                to,
                "[합주각] 메일 발송 점검",
                "메일 발송 점검",
                "이 메일이 보이면 서버가 메일을 보낼 수 있습니다. 아래는 실제 인증번호가 아니라 형식을 보여주는 예시입니다.",
                sixDigits(),
                10,
                "관리자가 발송 설정을 점검하려고 보낸 메일입니다."
        );
    }

    /** 인증번호. 앞자리가 0 이어도 여섯 자리를 유지한다. */
    public static String sixDigits() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    private void sendCodeMail(String to, String subject, String headline, String lead,
                              String code, int minutes, String notice) {
        String expiry = minutes + "분 안에 입력해주세요";

        Map<String, String> values = new LinkedHashMap<>();
        values.put("TITLE", subject);
        values.put("LOGO", logoUrl);
        values.put("HEADLINE", headline);
        values.put("LEAD", lead);
        values.put("CODE", code);
        values.put("EXPIRY", expiry);
        values.put("NOTICE", notice);

        String html = fill(layout, values);
        String text = plainText(headline, lead, code, expiry, notice);

        send(to, subject, html, text);
    }

    private void send(String to, String subject, String html, String text) {
        SendEmailRequest request = SendEmailRequest.builder()
                .fromEmailAddress(from)
                .destination(Destination.builder().toAddresses(to).build())
                .content(EmailContent.builder()
                        .simple(Message.builder()
                                .subject(utf8(subject))
                                .body(Body.builder()
                                        .html(utf8(html))
                                        .text(utf8(text))
                                        .build())
                                .build())
                        .build())
                .build();

        try {
            String messageId = ses.sendEmail(request).messageId();
            // 주소 전체를 로그에 남기지 않는다. 확인은 되고 유출되면 곤란한 건 가린다.
            log.info("메일 발송 to={} messageId={}", mask(to), messageId);
        } catch (SesV2Exception e) {
            // 왜 실패했는지 로그에 남긴다. 이게 없으면 "메일이 안 와요"에서 더 나아갈 수 없다.
            log.error("메일 발송 실패 to={} 이유={}", mask(to),
                    e.awsErrorDetails() == null ? e.getMessage() : e.awsErrorDetails().errorMessage());
            // IllegalStateException 으로 던지면 GlobalExceptionHandler 가 409 로 내보낸다.
            // 메일이 안 간 건 "충돌"이 아니라 "바깥 서비스가 지금 안 된다"는 뜻이라 503 이 맞다.
            throw new MailSendException("메일을 보내지 못했습니다.", e);
        }
    }

    private static Content utf8(String data) {
        return Content.builder().data(data).charset("UTF-8").build();
    }

    /** {{KEY}} 를 값으로 바꾼다. 값에 들어간 꺾쇠는 HTML 로 해석되지 않게 막는다. */
    private static String fill(String template, Map<String, String> values) {
        String result = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String replacement = "LOGO".equals(entry.getKey())
                    ? entry.getValue()                 // 주소는 우리가 넣는 값이라 그대로 둔다
                    : escape(entry.getValue());
            result = result.replace("{{" + entry.getKey() + "}}", replacement);
        }
        return result;
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /** HTML 을 못 읽는 메일 앱을 위한 같은 내용. */
    private static String plainText(String headline, String lead, String code,
                                    String expiry, String notice) {
        return FROM_NAME + "\n\n"
                + headline + "\n\n"
                + lead + "\n\n"
                + "    " + code + "\n\n"
                + expiry + ".\n\n"
                + notice + "\n\n"
                + "----\n"
                + "이 메일은 발신 전용입니다. 답장하셔도 확인하지 못합니다.\n"
                + SITE_URL + "\n";
    }

    /** jun***@gmail.com 형태로 줄인다. */
    private static String mask(String email) {
        int at = email.indexOf('@');
        if (at <= 1) return "***";
        int keep = Math.min(3, at);
        return email.substring(0, keep) + "***" + email.substring(at);
    }

    private static String readLayout() {
        try (InputStream in = new ClassPathResource("mail/layout.html").getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            // 템플릿이 없으면 메일을 만들 수 없다. 첫 발송 때 터지느니 띄울 때 터지는 게 낫다.
            throw new IllegalStateException("메일 템플릿(mail/layout.html)을 읽지 못했습니다.", e);
        }
    }
}
