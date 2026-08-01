package pl.stapik.cloud.security.apikey;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.net.InetAddress;
import java.net.UnknownHostException;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CidrMatcher {
    static boolean matches(String cidrOrIp, String remoteAddr) {
        try {
            String[] parts = cidrOrIp.split("/", 2);
            byte[] networkBytes = InetAddress.getByName(parts[0].trim()).getAddress();
            byte[] candidateBytes = InetAddress.getByName(remoteAddr).getAddress();

            if (networkBytes.length != candidateBytes.length) {
                return false;
            }

            int prefixLength = getPrefixLength(parts, networkBytes);
            int fullBytes = prefixLength / 8;
            int remainingBits = prefixLength % 8;

            for (int i = 0; i < fullBytes; i++) {
                if (networkBytes[i] != candidateBytes[i]) {
                    return false;
                }
            }
            if (remainingBits > 0) {
                int mask = (0xFF00 >> remainingBits) & 0xFF;
                return (networkBytes[fullBytes] & mask) == (candidateBytes[fullBytes] & mask);
            }
            return true;
        } catch (UnknownHostException | NumberFormatException e) {
            return false;
        }
    }

    private static int getPrefixLength(String[] parts, byte[] networkBytes) {
        return parts.length == 2
                ? Integer.parseInt(parts[1].trim())
                : networkBytes.length * 8;
    }
}
