package pl.stapik.cloud.admin.totp;

import java.io.ByteArrayOutputStream;
import java.util.Locale;

public final class Base32 {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int BITS_PER_CHAR = 5;
    private static final int BITS_PER_BYTE = 8;

    private Base32() {
    }

    public static String encode(byte[] data) {
        StringBuilder encoded = new StringBuilder();
        int buffer = 0;
        int bufferedBits = 0;

        for (byte value : data) {
            buffer = (buffer << BITS_PER_BYTE) | (value & 0xff);
            bufferedBits += BITS_PER_BYTE;
            while (bufferedBits >= BITS_PER_CHAR) {
                encoded.append(ALPHABET.charAt((buffer >> (bufferedBits - BITS_PER_CHAR)) & 31));
                bufferedBits -= BITS_PER_CHAR;
            }
            buffer &= (1 << bufferedBits) - 1;
        }

        if (bufferedBits > 0) {
            encoded.append(ALPHABET.charAt((buffer << (BITS_PER_CHAR - bufferedBits)) & 31));
        }
        return encoded.toString();
    }

    public static byte[] decode(String text) {
        String normalized = text.replace("=", "").replace(" ", "").toUpperCase(Locale.ROOT);
        ByteArrayOutputStream decoded = new ByteArrayOutputStream();
        int buffer = 0;
        int bufferedBits = 0;

        for (char character : normalized.toCharArray()) {
            int value = ALPHABET.indexOf(character);
            if (value < 0) {
                throw new IllegalArgumentException("Not a base32 character: " + character);
            }
            buffer = (buffer << BITS_PER_CHAR) | value;
            bufferedBits += BITS_PER_CHAR;
            if (bufferedBits >= BITS_PER_BYTE) {
                decoded.write((buffer >> (bufferedBits - BITS_PER_BYTE)) & 0xff);
                bufferedBits -= BITS_PER_BYTE;
            }
            buffer &= (1 << bufferedBits) - 1;
        }
        return decoded.toByteArray();
    }
}
