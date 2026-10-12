package pl.stapik.cloud.security.ratelimit;

import com.github.benmanes.caffeine.cache.Ticker;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginAttemptTrackerTest {

    private final AtomicLong nanos = new AtomicLong();
    private final Ticker ticker = nanos::get;
    private final LoginAttemptTracker tracker = new LoginAttemptTracker(3, Duration.ofMinutes(15), ticker);

    @Test
    void shouldLockUsernameAfterMaxFailedAttempts() {
        failTimes("admin", 3);

        assertThatThrownBy(() -> tracker.checkNotLocked("admin"))
                .isInstanceOf(TooManyRequestsException.class)
                .extracting(exception -> ((TooManyRequestsException) exception).getRetryAfter())
                .isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void shouldNotLockBeforeMaxFailedAttempts() {
        failTimes("admin", 2);

        assertThatCode(() -> tracker.checkNotLocked("admin")).doesNotThrowAnyException();
    }

    @Test
    void shouldReportRemainingLockoutTime() {
        failTimes("admin", 3);
        nanos.addAndGet(Duration.ofMinutes(5).toNanos());

        assertThatThrownBy(() -> tracker.checkNotLocked("admin"))
                .extracting(exception -> ((TooManyRequestsException) exception).getRetryAfter())
                .isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void shouldUnlockAfterLockoutDuration() {
        failTimes("admin", 3);
        nanos.addAndGet(Duration.ofMinutes(15).toNanos());

        assertThatCode(() -> tracker.checkNotLocked("admin")).doesNotThrowAnyException();
    }

    @Test
    void shouldClearFailuresOnReset() {
        failTimes("admin", 2);
        tracker.reset("admin");
        failTimes("admin", 2);

        assertThatCode(() -> tracker.checkNotLocked("admin")).doesNotThrowAnyException();
    }

    @Test
    void shouldIgnoreCaseAndSurroundingWhitespaceInUsername() {
        failTimes("Admin", 2);
        tracker.recordFailure(" admin ");

        assertThatThrownBy(() -> tracker.checkNotLocked("ADMIN")).isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void shouldTrackUsernamesIndependently() {
        failTimes("first", 3);

        assertThatCode(() -> tracker.checkNotLocked("second")).doesNotThrowAnyException();
    }

    private void failTimes(String username, int times) {
        for (int i = 0; i < times; i++) {
            tracker.recordFailure(username);
        }
    }
}
