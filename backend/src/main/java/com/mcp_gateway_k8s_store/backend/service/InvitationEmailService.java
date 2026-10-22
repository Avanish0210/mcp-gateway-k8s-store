package com.mcp_gateway_k8s_store.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class InvitationEmailService {
    private static final Logger log = LoggerFactory.getLogger(InvitationEmailService.class);

    private final JavaMailSender mailSender;
    private final boolean enabled;
    private final String host;
    private final String from;
    private final String username;
    private final String password;
    private final boolean smtpAuth;
    private final String frontendBaseUrl;

    public InvitationEmailService(JavaMailSender mailSender,
                                  @Value("${team.invitations.email-enabled:false}") boolean enabled,
                                  @Value("${spring.mail.host:}") String host,
                                  @Value("${team.invitations.from:}") String from,
                                  @Value("${spring.mail.username:}") String username,
                                  @Value("${spring.mail.password:}") String password,
                                  @Value("${spring.mail.properties.mail.smtp.auth:true}") boolean smtpAuth,
                                  @Value("${team.invitations.frontend-base-url:http://localhost:5173}") String frontendBaseUrl) {
        this.mailSender = mailSender;
        this.enabled = enabled;
        this.host = host;
        this.from = from;
        this.username = username;
        this.password = password;
        this.smtpAuth = smtpAuth;
        this.frontendBaseUrl = frontendBaseUrl.replaceAll("/+$", "");
    }

    public boolean isConfigured() {
        return enabled && host != null && !host.isBlank() && from != null && !from.isBlank()
                && (!smtpAuth || (username != null && !username.isBlank() && password != null && !password.isBlank()));
    }

    public void send(String email, String token) {
        if (!isConfigured()) throw new IllegalStateException("Invitation email is not configured");
        String inviteUrl = frontendBaseUrl + "/accept-invitation?token=" + token;
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("You are invited to join MCP Gateway");
        message.setText("You have been invited to join MCP Gateway as a team member.\n\n"
                + "Accept your invitation using this secure, single-use link:\n" + inviteUrl
                + "\n\nThis invitation expires according to the expiry shown in the application. "
                + "If you did not expect this email, you can ignore it.");
        try {
            mailSender.send(message);
        } catch (RuntimeException failure) {
            // Do not log the recipient, token, SMTP response, or credentials.
            log.warn("Team invitation email delivery failed ({})", failure.getClass().getSimpleName());
            throw new IllegalStateException("Invitation email could not be delivered", failure);
        }
    }
}
