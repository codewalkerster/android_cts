package com.android.compatibility.common.util;

import static junit.framework.TestCase.fail;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Inherit this class and implement {@link #executeShellCommand(String)} to be able to assert that
 * logcat contains what you want.
 */
public abstract class LogcatInspector {
    private static final int SMALL_LOGCAT_DELAY = 1000;

    /**
     * Should execute adb shell {@param command} and return an {@link InputStream} with the result.
     */
    protected abstract InputStream executeShellCommand(String command) throws IOException;

    /**
     * Attempts to clear logcat and logs an unique string using tag {@param tag}.
     *
     * <p>Clearing logcat is known to be unreliable. This unique string is returned and can be used
     * to find this point in the log even if clearing failed.
     *
     * @return a unique separator string.
     * @throws IOException if error while executing command.
     */
    public String clearAndMark(String tag) throws IOException {
        executeShellCommand("logcat -c");
        String uniqueString = ":::" + UUID.randomUUID().toString();
        executeShellCommand("log -t " + tag + " " + uniqueString);
        // This is to guarantee that we only return after the string has been logged, otherwise
        // in practice the case where calling Log.?(<message1>) right after clearAndMark() resulted
        // in <message1> appearing before the unique identifier. It's not guaranteed per the docs
        // that log command will have written when returning, so better be safe. 3s should be fine.
        assertLogcatContainsInOrder(tag, 3, uniqueString);
        return uniqueString;
    }

    /**
     * Wait for up to {@param maxTimeoutInSeconds} for the given {@param logcatStrings} strings to
     * appear in logcat in the given order. By passing the separator returned by {@link
     * #clearAndMark(String)} as the first string you can ensure that only logs emitted after that
     * call to clearAndMark() are found. Repeated strings are not supported.
     *
     * @throws AssertionError if the strings are not found in the given time.
     * @throws IOException if error while reading.
     */
    public void assertLogcatContainsInOrder(
            String filterTag, int maxTimeoutInSeconds, String... logcatStrings)
            throws AssertionError, IOException {
        long timeout = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(maxTimeoutInSeconds);
        int stringIndex = 0;
        while (timeout >= System.currentTimeMillis()) {
            InputStream logcatStream =
                    executeShellCommand("logcat -v brief -d " + filterTag + ":* *:S");
            BufferedReader logcat = new BufferedReader(new InputStreamReader(logcatStream));
            String line;
            stringIndex = 0;
            while ((line = logcat.readLine()) != null) {
                if (line.contains(logcatStrings[stringIndex])) {
                    stringIndex++;
                    if (stringIndex >= logcatStrings.length) {
                        drainAndClose(logcat);
                        return;
                    }
                }
            }
            closeQuietly(logcat);
            try {
                // In case the key has not been found, wait for the log to update before
                // performing the next search.
                Thread.sleep(SMALL_LOGCAT_DELAY);
            } catch (InterruptedException e) {
                fail("Thread interrupted unexpectedly: " + e.getMessage());
            }
        }
        fail(
                "Couldn't find "
                        + logcatStrings[stringIndex]
                        + (stringIndex > 0 ? " after " + logcatStrings[stringIndex - 1] : "")
                        + " within "
                        + maxTimeoutInSeconds
                        + " seconds ");
    }

    private static void drainAndClose(BufferedReader reader) {
        try {
            while (reader.read() >= 0) {
                // do nothing.
            }
        } catch (IOException ignored) {
        }
        closeQuietly(reader);
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (RuntimeException rethrown) {
                throw rethrown;
            } catch (Exception ignored) {
            }
        }
    }
}
