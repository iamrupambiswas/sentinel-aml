package com.sentinel.aml.common.aspect;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingAspectRedactionTest {

    @Test
    void masksPiiInRecordToString() {
        String args = "[CustomerRegistrationRequest[type=INDIVIDUAL, fullName=Asha Rao, idNumber=PAN-ABCD1234E, dob=1990-01-01]]";
        String out = LoggingAspect.redact(args);
        assertThat(out).doesNotContain("ABCD1234E").contains("idNumber=***").contains("fullName=Asha Rao").contains("dob=1990-01-01");
    }

    @Test
    void masksSecretsInJsonAndKeepsOtherFields() {
        String out = LoggingAspect.redact("{\"accessToken\":\"eyJhbGciOi.x.y\",\"username\":\"admin1\"}");
        assertThat(out).doesNotContain("eyJhbGciOi").contains("\"username\":\"admin1\"");
    }

    @Test
    void leavesOrdinaryArgumentsAlone() {
        assertThat(LoggingAspect.redact("[CTR_THRESHOLD, 5]")).isEqualTo("[CTR_THRESHOLD, 5]");
        assertThat(LoggingAspect.redact(null)).isNull();
    }
}
