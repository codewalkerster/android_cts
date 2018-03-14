/*
 * Copyright 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.media.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.media.MediaItem2;
import android.media.MediaMetadata2;
import android.media.MediaSession2.PlaylistParams;
import android.media.SessionToken2;
import android.media.session.MediaSessionManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Utilities for tests.
 */
public final class TestUtils {
    private static final int WAIT_TIME_MS = 1000;
    private static final int WAIT_SERVICE_TIME_MS = 5000;

    /**
     * Finds the session with id in this test package.
     *
     * @param context
     * @param id
     * @return
     */
    public static SessionToken2 getServiceToken(Context context, String id) {
        MediaSessionManager manager =
                (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
        List<SessionToken2> tokens = manager.getSessionServiceTokens();
        for (int i = 0; i < tokens.size(); i++) {
            SessionToken2 token = tokens.get(i);
            if (context.getPackageName().equals(token.getPackageName())
                    && id.equals(token.getId())) {
                return token;
            }
        }
        fail("Failed to find service");
        return null;
    }

    /**
     * Compares contents of two bundles.
     *
     * @param a a bundle
     * @param b another bundle
     * @return {@code true} if two bundles are the same. {@code false} otherwise. This may be
     *     incorrect if any bundle contains a bundle.
     */
    public static boolean equals(Bundle a, Bundle b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        if (!a.keySet().containsAll(b.keySet())
                || !b.keySet().containsAll(a.keySet())) {
            return false;
        }
        for (String key : a.keySet()) {
            if (!Objects.equals(a.get(key), b.get(key))) {
                return false;
            }
        }
        return true;
    }

    public static void ensurePlaylistParamsModeEquals(PlaylistParams a, PlaylistParams b) {
        assertEquals(a.getRepeatMode(), b.getRepeatMode());
        assertEquals(a.getShuffleMode(), b.getShuffleMode());
    }

    /**
     * Create a playlist for testing purpose
     * <p>
     * Caller's method name will be used for prefix of each media item's media id.
     *
     * @param context context
     * @return the newly created playlist
     */
    public static List<MediaItem2> createPlaylist(Context context) {
        final List<MediaItem2> list = new ArrayList<>();
        String caller = Thread.currentThread().getStackTrace()[1].getMethodName();
        list.add(new MediaItem2.Builder(context, MediaItem2.FLAG_PLAYABLE)
                .setMediaId(caller + "_item_1").build());
        list.add(new MediaItem2.Builder(context, MediaItem2.FLAG_PLAYABLE)
                .setMediaId(caller + "_item_2").build());
        return list;
    }

    /**
     * Create a media item with the metadata for testing purpose.
     *
     * @param context context
     * @return the newly created media item
     * @see #createMetadata(Context)
     */
    public static MediaItem2 createMediaItemWithMetadata(Context context) {
        return new MediaItem2.Builder(context, MediaItem2.FLAG_PLAYABLE)
                .setMetadata(createMetadata(context)).build();
    }

    /**
     * Create a media metadata for testing purpose.
     * <p>
     * Caller's method name will be used for the media id.
     *
     * @param context context
     * @return the newly created media item
     */
    public static MediaMetadata2 createMetadata(Context context) {
        String mediaId = Thread.currentThread().getStackTrace()[1].getMethodName();
        return new MediaMetadata2.Builder(context)
                .putString(MediaMetadata2.METADATA_KEY_MEDIA_ID, mediaId).build();
    }

    /**
     * Handler that always waits until the Runnable finishes.
     */
    public static class SyncHandler extends Handler {
        public SyncHandler(Looper looper) {
            super(looper);
        }

        public void postAndSync(Runnable runnable) throws InterruptedException {
            if (getLooper() == Looper.myLooper()) {
                runnable.run();
            } else {
                final CountDownLatch latch = new CountDownLatch(1);
                post(()->{
                    runnable.run();
                    latch.countDown();
                });
                assertTrue(latch.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS));
            }
        }
    }
}