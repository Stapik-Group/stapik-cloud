package pl.stapik.cloud.document.conflict;

import org.junit.jupiter.api.Test;
import pl.stapik.cloud.document.conflict.impl.ShadowCopyResolver;
import pl.stapik.cloud.documentslot.data.ConflictStrategy;

import static org.assertj.core.api.Assertions.assertThat;

class ShadowCopyResolverTest {

    private final ShadowCopyResolver resolver = new ShadowCopyResolver();

    @Test
    void shouldSupportShadowCopyStrategy() {
        assertThat(resolver.supports()).isEqualTo(ConflictStrategy.LAST_WRITE_WINS_WITH_SHADOW_COPY);
    }

    @Test
    void shouldAcceptWriteWhenClientKnowsCurrentContentHash() {
        // when
        ConflictDecision decision = resolver.resolve("hash-current", "hash-current");

        // then
        assertThat(decision.writeAccepted()).isTrue();
        assertThat(decision.preserveDiscardedVersion()).isFalse();
    }

    @Test
    void shouldRejectWriteWhenClientContentHashIsStale() {
        // when
        ConflictDecision decision = resolver.resolve("hash-current", "hash-stale");

        // then
        assertThat(decision.writeAccepted()).isFalse();
        assertThat(decision.preserveDiscardedVersion()).isTrue();
    }

    @Test
    void shouldRejectWriteWhenClientContentHashIsNull() {
        // when
        ConflictDecision decision = resolver.resolve("hash-current", null);

        // then
        assertThat(decision.writeAccepted()).isFalse();
        assertThat(decision.preserveDiscardedVersion()).isTrue();
    }
}
