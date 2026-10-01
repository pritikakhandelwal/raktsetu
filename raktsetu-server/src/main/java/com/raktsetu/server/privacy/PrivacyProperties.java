package com.raktsetu.server.privacy;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Who runs this RaktSetu network and how people reach them about their data, under {@code raktsetu.privacy.*}.
 *
 * @param organisation       the organisation responsible for the data (the Data Fiduciary under the DPDP Act), or
 *                           null if not set
 * @param contact            how to reach its grievance officer, for example an e-mail address
 * @param noticeVersion      the version of the privacy notice in force; recorded when someone agrees to alerts
 * @param alertRetentionDays how long sent and failed alerts (which contain a phone number) are kept
 */
@ConfigurationProperties("raktsetu.privacy")
public record PrivacyProperties(String organisation, String contact, String noticeVersion, Integer alertRetentionDays) {

    public PrivacyProperties {
        organisation = blank(organisation) ? null : organisation.trim();
        contact = blank(contact) ? null : contact.trim();
        noticeVersion = blank(noticeVersion) ? "2026-10-01" : noticeVersion.trim();
        alertRetentionDays = alertRetentionDays == null || alertRetentionDays < 1 ? 90 : alertRetentionDays;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
