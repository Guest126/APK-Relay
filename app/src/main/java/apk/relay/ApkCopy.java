// SPDX-License-Identifier: Unlicense
package apk.relay;

import java.io.*;

final class ApkCopy {
    static long copy(InputStream in, OutputStream out) throws IOException {
        if (in == null) throw new IOException("Cannot open file");
        byte[] buffer = new byte[65536];
        long total = 0;
        int n;
        while ((n = in.read(buffer)) != -1) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
            total += n;
            out.write(buffer, 0, n);
        }
        if (total == 0) throw new IOException("Empty file");
        return total;
    }
}
