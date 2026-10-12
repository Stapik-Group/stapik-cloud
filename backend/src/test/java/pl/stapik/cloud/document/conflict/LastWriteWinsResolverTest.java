package pl.stapik.cloud.document.conflict;

import org.junit.jupiter.api.Test;
import pl.stapik.cloud.document.conflict.impl.LastWriteWinsResolver;
import pl.stapik.cloud.documentslot.data.ConflictStrategy;

import static org.assertj.core.api.Assertions.assertThat;

class LastWriteWinsResolverTest {

    private final LastWriteWinsResolver resolver = new LastWriteWinsResolver();

    @Test
    void shouldSupportLastWriteWinsStrategy() {
        assertThat(resolver.supports()).isEqualTo(ConflictStrategy.LAST_WRITE_WINS);
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
        assertThat(decision.preserveDiscardedVersion()).isFalse();
    }

    @Test
    void shouldRejectWriteWhenClientContentHashIsNull() {
        // when
        ConflictDecision decision = resolver.resolve("hash-current", null);

        // then
        assertThat(decision.writeAccepted()).isFalse();
        assertThat(decision.preserveDiscardedVersion()).isFalse();
    }
}
