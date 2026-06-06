package com.demo.ragchat.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitServiceTest {

    @Test
    void checkRejectsRequestsAboveConfiguredLimit() {
        RateLimitService service = service(2);

        assertThat(service.check("reader").allowed()).isTrue();
        assertThat(service.check("reader").allowed()).isTrue();
        RateLimitService.RateLimitDecision rejected = service.check("reader");

        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.limit()).isEqualTo(2);
        assertThat(rejected.retryAfterSeconds()).isGreaterThan(0);
    }

    @Test
    void checkTracksActorsSeparately() {
        RateLimitService service = service(1);

        assertThat(service.check("reader-a").allowed()).isTrue();
        assertThat(service.check("reader-a").allowed()).isFalse();
        assertThat(service.check("reader-b").allowed()).isTrue();
    }

    @Test
    void checkAllowsAllRequestsWhenLimitIsZero() {
        RateLimitService service = service(0);

        assertThat(service.check("reader").allowed()).isTrue();
        assertThat(service.check("reader").allowed()).isTrue();
        assertThat(service.check("reader").limit()).isZero();
    }

    private RateLimitService service(int limit) {
        RateLimitService service = new RateLimitService();
        ReflectionTestUtils.setField(service, "maxRequestsPerMinute", limit);
        return service;
    }
}
