package pl.stapik.cloud.asset.storage.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.stapik.cloud.asset.AssetChunkTooLargeException;
import pl.stapik.cloud.common.crypto.EncryptionException;
import pl.stapik.cloud.common.crypto.EnvelopeCipher;

import javax.crypto.SecretKey;
import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

@Component
@RequiredArgsConstructor
public class AssetFileCipher {

    private static final byte[] MAGIC = "STAPIKA1".getBytes(StandardCharsets.US_ASCII);
    private static final int HEADER_BYTES = MAGIC.length + EnvelopeCipher.WRAPPED_KEY_BYTES;
    private static final int FRAME_PLAINTEXT_BYTES = 64 * 1024;
    private static final int FRAME_LENGTH_BYTES = Integer.BYTES;
    private static final int MAX_SEALED_BYTES = FRAME_PLAINTEXT_BYTES + EnvelopeCipher.SEAL_OVERHEAD_BYTES;
    private static final String ENCRYPTING_SUFFIX = ".encrypting";

    private final EnvelopeCipher envelopeCipher;

    public boolean isEncrypted(Path file) throws IOException {
        try (InputStream stream = Files.newInputStream(file)) {
            return Arrays.equals(stream.readNBytes(MAGIC.length), MAGIC);
        }
    }

    public Appender create(Path file, OpenOption... options) throws IOException {
        SecretKey dataKey = envelopeCipher.newDataKey();
        FileChannel channel = FileChannel.open(file, options);
        try {
            ByteBuffer header = ByteBuffer.allocate(HEADER_BYTES).put(MAGIC).put(envelopeCipher.wrap(dataKey)).flip();
            writeFully(channel, header);
            return new Appender(channel, dataKey, 0);
        } catch (IOException | RuntimeException e) {
            channel.close();
            throw e;
        }
    }

    public Appender resume(Path file, long plaintextOffset) throws IOException {
        FileChannel channel = FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE);
        try {
            SecretKey dataKey = readDataKey(channel);
            long position = HEADER_BYTES;
            long plaintextBytes = 0;
            long frames = 0;

            while (plaintextBytes < plaintextOffset) {
                int sealedLength = readFrameLength(channel, position);
                position += FRAME_LENGTH_BYTES + sealedLength;
                plaintextBytes += sealedLength - EnvelopeCipher.SEAL_OVERHEAD_BYTES;
                frames++;
            }

            if (plaintextBytes != plaintextOffset || position > channel.size()) {
                throw new IOException("Upload file does not end at the committed offset: " + plaintextOffset);
            }

            channel.truncate(position);
            channel.position(position);
            return new Appender(channel, dataKey, frames);
        } catch (IOException | RuntimeException e) {
            channel.close();
            throw e;
        }
    }

    public InputStream open(Path file) throws IOException {
        InputStream stream = new BufferedInputStream(Files.newInputStream(file));
        try {
            SecretKey dataKey = parseDataKey(stream.readNBytes(HEADER_BYTES));
            return new DecryptingInputStream(stream, dataKey);
        } catch (IOException | RuntimeException e) {
            stream.close();
            throw e;
        }
    }

    public long plaintextSize(Path file) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            long end = channel.size();
            long position = HEADER_BYTES;
            long size = 0;

            while (position < end) {
                int sealedLength = readFrameLength(channel, position);
                position += FRAME_LENGTH_BYTES + sealedLength;
                size += sealedLength - EnvelopeCipher.SEAL_OVERHEAD_BYTES;
            }

            if (position != end) {
                throw new IOException("Asset file is truncated: " + file);
            }
            return size;
        }
    }

    public void secure(Path file) throws IOException {
        if (isEncrypted(file)) {
            rewrapDataKey(file);
        } else {
            encryptInPlace(file);
        }
    }

    private void encryptInPlace(Path file) throws IOException {
        Path encrypted = file.resolveSibling(file.getFileName() + ENCRYPTING_SUFFIX);
        try (InputStream plain = Files.newInputStream(file);
             Appender appender = create(encrypted, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            appender.write(plain, Long.MAX_VALUE);
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(encrypted);
            throw e;
        }
        Files.move(encrypted, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private void rewrapDataKey(Path file) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            ByteBuffer current = ByteBuffer.allocate(EnvelopeCipher.WRAPPED_KEY_BYTES);
            readFully(channel, current, MAGIC.length);
            if (envelopeCipher.isWrappedWithActiveKey(current.array())) {
                return;
            }

            ByteBuffer rewrapped = ByteBuffer.wrap(envelopeCipher.rewrap(current.array()));
            long position = MAGIC.length;
            while (rewrapped.hasRemaining()) {
                position += channel.write(rewrapped, position);
            }
        }
    }

    private SecretKey readDataKey(FileChannel channel) throws IOException {
        ByteBuffer header = ByteBuffer.allocate(HEADER_BYTES);
        readFully(channel, header, 0);
        return parseDataKey(header.array());
    }

    private SecretKey parseDataKey(byte[] header) throws IOException {
        boolean encrypted = header.length == HEADER_BYTES
                && Arrays.equals(Arrays.copyOfRange(header, 0, MAGIC.length), MAGIC);
        if (!encrypted) {
            throw new IOException("File is not an encrypted asset");
        }
        return envelopeCipher.unwrap(Arrays.copyOfRange(header, MAGIC.length, HEADER_BYTES));
    }

    private int readFrameLength(FileChannel channel, long position) throws IOException {
        ByteBuffer length = ByteBuffer.allocate(FRAME_LENGTH_BYTES);
        readFully(channel, length, position);

        int sealedLength = length.getInt(0);
        if (sealedLength < EnvelopeCipher.SEAL_OVERHEAD_BYTES || sealedLength > MAX_SEALED_BYTES) {
            throw new IOException("Encrypted file is corrupted");
        }
        return sealedLength;
    }

    private static void readFully(FileChannel channel, ByteBuffer target, long position) throws IOException {
        long next = position;
        while (target.hasRemaining()) {
            int read = channel.read(target, next);
            if (read < 0) {
                throw new IOException("Encrypted file is shorter than expected");
            }
            next += read;
        }
    }

    private static void writeFully(FileChannel channel, ByteBuffer source) throws IOException {
        while (source.hasRemaining()) {
            channel.write(source);
        }
    }

    private static byte[] frameAssociatedData(long frameIndex) {
        return ByteBuffer.allocate(Long.BYTES).putLong(frameIndex).array();
    }

    public final class Appender implements Closeable {

        private final FileChannel channel;
        private final SecretKey dataKey;
        private long frameIndex;

        private Appender(FileChannel channel, SecretKey dataKey, long frameIndex) {
            this.channel = channel;
            this.dataKey = dataKey;
            this.frameIndex = frameIndex;
        }

        public long write(InputStream source, long maxBytes) throws IOException {
            byte[] buffer = new byte[FRAME_PLAINTEXT_BYTES];
            long total = 0;
            int read;

            while ((read = source.readNBytes(buffer, 0, buffer.length)) > 0) {
                total += read;
                if (total > maxBytes) {
                    throw new AssetChunkTooLargeException(maxBytes);
                }
                writeFrame(buffer, read);
            }

            return total;
        }

        @Override
        public void close() throws IOException {
            channel.close();
        }

        private void writeFrame(byte[] buffer, int length) throws IOException {
            byte[] sealed = envelopeCipher.seal(dataKey, buffer, length, frameAssociatedData(frameIndex++));
            ByteBuffer frame = ByteBuffer.allocate(FRAME_LENGTH_BYTES + sealed.length).putInt(sealed.length).put(sealed).flip();
            writeFully(channel, frame);
        }
    }

    private final class DecryptingInputStream extends InputStream {

        private final InputStream source;
        private final SecretKey dataKey;
        private byte[] frame = new byte[0];
        private int position;
        private long frameIndex;
        private boolean finished;

        private DecryptingInputStream(InputStream source, SecretKey dataKey) {
            this.source = source;
            this.dataKey = dataKey;
        }

        @Override
        public int read() throws IOException {
            byte[] single = new byte[1];
            return read(single, 0, 1) == -1 ? -1 : single[0] & 0xff;
        }

        @Override
        public int read(byte[] target, int offset, int length) throws IOException {
            if (length == 0) {
                return 0;
            }

            while (position == frame.length) {
                if (finished || !nextFrame()) {
                    return -1;
                }
            }

            int count = Math.min(length, frame.length - position);
            System.arraycopy(frame, position, target, offset, count);
            position += count;
            return count;
        }

        @Override
        public void close() throws IOException {
            source.close();
        }

        private boolean nextFrame() throws IOException {
            byte[] lengthBytes = source.readNBytes(FRAME_LENGTH_BYTES);
            if (lengthBytes.length == 0) {
                finished = true;
                return false;
            }
            if (lengthBytes.length < FRAME_LENGTH_BYTES) {
                throw new IOException("Encrypted file is truncated");
            }

            int sealedLength = ByteBuffer.wrap(lengthBytes).getInt();
            if (sealedLength < EnvelopeCipher.SEAL_OVERHEAD_BYTES || sealedLength > MAX_SEALED_BYTES) {
                throw new IOException("Encrypted file is corrupted");
            }

            byte[] sealed = source.readNBytes(sealedLength);
            if (sealed.length < sealedLength) {
                throw new IOException("Encrypted file is truncated");
            }

            try {
                frame = envelopeCipher.open(dataKey, sealed, sealedLength, frameAssociatedData(frameIndex++));
            } catch (EncryptionException e) {
                throw new IOException("Encrypted file is corrupted or was modified", e);
            }
            position = 0;
            return true;
        }
    }
}
