package pl.stapik.cloud.common.crypto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HashingServiceTest {

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private HashingService hashingService;

    @Test
    void shouldHashRawString() {
        // given
        String raw = "secret-key";
        String hashed = "encoded-hash";
        given(passwordEncoder.encode(raw)).willReturn(hashed);

        // when
        String result = hashingService.hash(raw);

        // then
        assertThat(result).isEqualTo(hashed);
        verify(passwordEncoder).encode(raw);
    }

    @Test
    void shouldMatchRawWithHashed() {
        // given
        String raw = "secret-key";
        String hashed = "encoded-hash";
        given(passwordEncoder.matches(raw, hashed)).willReturn(true);

        // when
        boolean matches = hashingService.matches(raw, hashed);

        // then
        assertThat(matches).isTrue();
        verify(passwordEncoder).matches(raw, hashed);
    }
}