// SPDX-License-Identifier: Unlicense
package apk.relay;

import java.io.*;
import java.util.Arrays;

public class ApkCopyTest {
    interface Check { void run() throws IOException; }
    static void fails(Check c) throws Exception {
        try { c.run(); throw new AssertionError("Expected IOException"); } catch (IOException expected) {}
    }
    public static void main(String[] args) throws Exception {
        byte[] data = new byte[150000];
        for (int i = 0; i < data.length; i++) data[i] = (byte)i;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (ApkCopy.copy(new ByteArrayInputStream(data), output) != data.length || !Arrays.equals(data, output.toByteArray()))
            throw new AssertionError("Bytes changed");
        // Exercise a stream above the former limit without allocating a large file.
        long size = (1L << 30) + 1;
        InputStream large = new InputStream() {
            long remaining = size;
            public int read() { return remaining-- > 0 ? 0 : -1; }
            public int read(byte[] buffer, int offset, int length) {
                if (remaining == 0) return -1;
                int n = (int)Math.min(remaining, length);
                remaining -= n;
                return n;
            }
        };
        if (ApkCopy.copy(large, OutputStream.nullOutputStream()) != size) throw new AssertionError("Size limit remains");
        fails(() -> ApkCopy.copy(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream()));
        fails(() -> ApkCopy.copy(null, new ByteArrayOutputStream()));
        fails(() -> ApkCopy.copy(new InputStream() {
            public int read() throws IOException { throw new IOException("source lost"); }
        }, new ByteArrayOutputStream()));
        Thread.currentThread().interrupt();
        fails(() -> ApkCopy.copy(new ByteArrayInputStream(data), new ByteArrayOutputStream()));
        Thread.interrupted();
        System.out.println("PASS: exact bytes, over 1 GiB without a size cap, empty input, null stream, read failure, cancellation");
    }
}
