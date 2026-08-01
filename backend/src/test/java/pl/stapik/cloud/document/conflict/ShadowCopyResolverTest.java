package pl.stapik.cloud.document.conflict;

import org.junit.jupiter.api.Test;
import pl.stapik.cloud.document.conflict.impl.ShadowCopyResolver;
import pl.stapik.cloud.documentslot.data.ConflictStrategy;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ShadowCopyResolverTest {

    private final ShadowCopyResolver resolver = new ShadowCopyResolver();

    @Test
    void shouldSupportShadowCopyStrategy() {
        assertThat(resolver.supports()).isEqualTo(ConflictStrategy.LAST_WRITE_WINS_WITH_SHADOW_COPY);
    }

    @Test
    void shouldAcceptWriteWhenClientKnowsExactCurrentVersion() {
        // given
        Instant existingUpdatedAt = Instant.parse("2026-07-18T10:00:00Z");
        Instant clientLastKnownUpdate = Instant.parse("2026-07-18T10:00:00Z");

        // when
        ConflictDecision decision = resolver.resolve(existingUpdatedAt, clientLastKnownUpdate);

        // then
        assertThat(decision.writeAccepted()).isTrue();
        assertThat(decision.preserveDiscardedVersion()).isFalse();
    }

    @Test
    void shouldAcceptWriteWhenClientIsNewerThanExisting() {
        // given
        Instant existingUpdatedAt = Instant.parse("2026-07-18T10:00:00Z");
        Instant clientLastKnownUpdate = Instant.parse("2026-07-18T11:00:00Z");

        // when
        ConflictDecision decision = resolver.resolve(existingUpdatedAt, clientLastKnownUpdate);

        // then
        assertThat(decision.writeAccepted()).isTrue();
        assertThat(decision.preserveDiscardedVersion()).isFalse();
    }

    @Test
    void shouldRejectWriteAndPreserveDiscardedVersionWhenClientIsBehind() {
        // given
        Instant existingUpdatedAt = Instant.parse("2026-07-18T10:00:00Z");
        Instant clientLastKnownUpdate = Instant.parse("2026-07-18T09:00:00Z");

        // when
        ConflictDecision decision = resolver.resolve(existingUpdatedAt, clientLastKnownUpdate);

        // then
        assertThat(decision.writeAccepted()).isFalse();
        assertThat(decision.preserveDiscardedVersion()).isTrue();
    }

    @Test
    void shouldRejectWriteAndPreserveDiscardedVersionWhenClientLastKnownUpdateIsNull() {
        // given
        Instant existingUpdatedAt = Instant.parse("2026-07-18T10:00:00Z");

        // when
        ConflictDecision decision = resolver.resolve(existingUpdatedAt, null);

        // then
        assertThat(decision.writeAccepted()).isFalse();
        assertThat(decision.preserveDiscardedVersion()).isTrue();
    }
}