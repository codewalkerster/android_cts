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

import static junit.framework.Assert.assertEquals;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaController2;
import android.media.MediaController2.ControllerCallback;
import android.media.MediaItem2;
import android.media.MediaMetadata2;
import android.media.MediaPlayerBase;
import android.media.MediaPlaylistAgent;
import android.media.MediaSession2;
import android.media.MediaSession2.Command;
import android.media.MediaSession2.CommandGroup;
import android.media.MediaSession2.ControllerInfo;
import android.media.MediaSession2.SessionCallback;
import android.media.Rating2;
import android.media.SessionToken2;
import android.media.VolumeProvider2;
import android.media.cts.TestServiceRegistry.SessionServiceCallback;
import android.media.cts.TestUtils.SyncHandler;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.os.ResultReceiver;
import android.support.annotation.NonNull;
import android.support.test.filters.FlakyTest;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tests {@link MediaController2}.
 */
// TODO(jaewan): Implement host-side test so controller and session can run in different processes.
// TODO(jaewan): Fix flaky failure -- see MediaController2Impl.getController()
// TODO(jaeawn): Revisit create/close session in the sHandler. It's no longer necessary.
@RunWith(AndroidJUnit4.class)
@SmallTest
@FlakyTest
public class MediaController2Test extends MediaSession2TestBase {
    private static final String TAG = "MediaController2Test";

    PendingIntent mIntent;
    MediaSession2 mSession;
    MediaController2 mController;
    MockPlayer mPlayer;

    // Note: If your tests needs notifying an event from the playlist agent, don't use this.
    //       Here's the reason. When this notifies an event, it passes its mInstance to the session,
    //       but the mInstance differs from the mMockAgent so session discards event from this.
    MockPlaylistAgent mMockAgent;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        final Intent sessionActivity = new Intent(mContext, MockActivity.class);
        // Create this test specific MediaSession2 to use our own Handler.
        mIntent = PendingIntent.getActivity(mContext, 0, sessionActivity, 0);

        mPlayer = new MockPlayer(1);
        mMockAgent = spy(new MockPlaylistAgent(mContext));
        mSession = new MediaSession2.Builder(mContext)
                .setPlayer(mPlayer)
                .setPlaylistAgent(mMockAgent)
                .setSessionCallback(sHandlerExecutor, new SessionCallback(mContext) {
                    @Override
                    public CommandGroup onConnect(MediaSession2 session,
                            ControllerInfo controller) {
                        if (Process.myUid() == controller.getUid()) {
                            return super.onConnect(session, controller);
                        }
                        return null;
                    }

                    @Override
                    public void onPlaylistMetadataChanged(MediaSession2 session,
                            MediaPlaylistAgent playlistAgent,
                            MediaMetadata2 metadata) {
                        super.onPlaylistMetadataChanged(session, playlistAgent, metadata);
                    }
                })
                .setSessionActivity(mIntent)
                .setId(TAG).build();
        mController = createController(mSession.getToken());
        TestServiceRegistry.getInstance().setHandler(sHandler);
    }

    @After
    @Override
    public void cleanUp() throws Exception {
        super.cleanUp();
        if (mSession != null) {
            mSession.close();
        }
        TestServiceRegistry.getInstance().cleanUp();
    }

    /**
     * Test if the {@link MediaSession2TestBase.TestControllerCallback} wraps the callback proxy
     * without missing any method.
     */
    @Test
    public void testTestControllerCallback() {
        Method[] methods = TestControllerCallback.class.getMethods();
        assertNotNull(methods);
        for (int i = 0; i < methods.length; i++) {
            // For any methods in the controller callback, TestControllerCallback should have
            // overriden the method and call matching API in the callback proxy.
            assertNotEquals("TestControllerCallback should override " + methods[i]
                            + " and call callback proxy",
                    ControllerCallback.class, methods[i].getDeclaringClass());
        }
    }

    @Test
    public void testPlay() {
        mController.play();
        try {
            assertTrue(mPlayer.mCountDownLatch.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS));
        } catch (InterruptedException e) {
            fail(e.getMessage());
        }
        assertTrue(mPlayer.mPlayCalled);
    }

    @Test
    public void testPause() {
        mController.pause();
        try {
            assertTrue(mPlayer.mCountDownLatch.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS));
        } catch (InterruptedException e) {
            fail(e.getMessage());
        }
        assertTrue(mPlayer.mPauseCalled);
    }

    @Ignore
    @Test
    public void testStop() {
        mController.stop();
        try {
            assertTrue(mPlayer.mCountDownLatch.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS));
        } catch (InterruptedException e) {
            fail(e.getMessage());
        }
        assertTrue(mPlayer.mStopCalled);
    }

    @Test
    public void testPrepare() {
        mController.prepare();
        try {
            assertTrue(mPlayer.mCountDownLatch.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS));
        } catch (InterruptedException e) {
            fail(e.getMessage());
        }
        assertTrue(mPlayer.mPrepareCalled);
    }

    @Ignore
    @Test
    public void testFastForward() {
        mController.fastForward();
        try {
            assertTrue(mPlayer.mCountDownLatch.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS));
        } catch (InterruptedException e) {
            fail(e.getMessage());
        }
        assertTrue(mPlayer.mFastForwardCalled);
    }

    @Ignore
    @Test
    public void testRewind() {
        mController.rewind();
        try {
            assertTrue(mPlayer.mCountDownLatch.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS));
        } catch (InterruptedException e) {
            fail(e.getMessage());
        }
        assertTrue(mPlayer.mRewindCalled);
    }

    @Test
    public void testSeekTo() {
        final long seekPosition = 12125L;
        mController.seekTo(seekPosition);
        try {
            assertTrue(mPlayer.mCountDownLatch.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS));
        } catch (InterruptedException e) {
            fail(e.getMessage());
        }
        assertTrue(mPlayer.mSeekToCalled);
        assertEquals(seekPosition, mPlayer.mSeekPosition);
    }

    @Test
    public void testGettersAfterConnected() throws InterruptedException {
        final int state = MediaPlayerBase.PLAYER_STATE_PLAYING;
        final long position = 150000;
        final long bufferedPosition = 900000;

        mPlayer.mLastPlayerState = state;
        mPlayer.mCurrentPosition = position;
        mPlayer.mBufferedPosition = bufferedPosition;

        MediaController2 controller = createController(mSession.getToken());
        assertEquals(state, controller.getPlayerState());
        assertEquals(bufferedPosition, controller.getBufferedPosition());
        // TODO (jaewan): Enable this test when Session2/Controller2's get(set)PlaybackSpeed
        //                is implemented. (b/74093080)
        //assertEquals(speed, controller.getPlaybackSpeed());
        //assertEquals(position + speed * elapsedTime, controller.getPosition(), delta);
    }

    @Test
    public void testGetSessionActivity() {
        PendingIntent sessionActivity = mController.getSessionActivity();
        assertEquals(mContext.getPackageName(), sessionActivity.getCreatorPackage());
        assertEquals(Process.myUid(), sessionActivity.getCreatorUid());
    }

    @Test
    public void testSetPlaylist() {
        final List<MediaItem2> list = TestUtils.createPlaylist(mContext, 2);
        mController.setPlaylist(list, null);
        verify(mMockAgent, timeout(TIMEOUT_MS).atLeastOnce()).setPlaylist(argThat((playlist) -> {
            assertNotNull(playlist);
            assertEquals(list.size(), playlist.size());
            for (int i = 0; i < list.size(); i++) {
                assertEquals(list.get(i).getMediaId(), playlist.get(i).getMediaId());
            }
            return true;
        }), isNull());
    }

    /**
     * This also tests {@link ControllerCallback#onPlaylistChanged(
     * MediaController2, MediaPlaylistAgent, List, MediaMetadata2)}.
     */
    @Test
    public void testGetPlaylist() throws InterruptedException {
        final List<MediaItem2> testList = TestUtils.createPlaylist(mContext, 2);
        final AtomicReference<List<MediaItem2>> listFromCallback = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);
        final ControllerCallback callback = new ControllerCallback() {
            @Override
            public void onPlaylistChanged(MediaController2 controller,
                    MediaPlaylistAgent playlistAgent, List<MediaItem2> playlist,
                    MediaMetadata2 metadata) {
                assertNotNull(playlist);
                assertEquals(testList.size(), playlist.size());
                for (int i = 0; i < playlist.size(); i++) {
                    assertEquals(testList.get(i).getMediaId(), playlist.get(i).getMediaId());
                }
                listFromCallback.set(playlist);
                latch.countDown();
            }
        };
        // Note: Can't use mockito here. See comments at mMockAgent.
        final MediaPlaylistAgent agent = new MediaPlaylistAgent(mContext) {
            @Override
            public List<MediaItem2> getPlaylist() {
                return testList;
            }
        };
        try (final MediaSession2 session = new MediaSession2.Builder(mContext)
                .setPlayer(mPlayer)
                .setId("testControllerCallback_onPlaylistChanged")
                .setSessionCallback(sHandlerExecutor, new SessionCallback(mContext) {})
                .setPlaylistAgent(agent)
                .build()) {
            MediaController2 controller = createController(
                    session.getToken(), true, callback);
            agent.notifyPlaylistChanged();
            assertTrue(latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
            assertEquals(listFromCallback.get(), controller.getPlaylist());
        }
    }

    @Test
    public void testUpdatePlaylistMetadata() {
        final MediaMetadata2 testMetadata = TestUtils.createMetadata(mContext);
        mController.updatePlaylistMetadata(testMetadata);
        verify(mMockAgent, timeout(TIMEOUT_MS).atLeastOnce())
                .updatePlaylistMetadata(argThat((metadata) -> {
                    assertNotNull(metadata);
                    assertEquals(testMetadata.getMediaId(), metadata.getMediaId());
                    return true;
                }));
    }

    @Test
    public void testGetPlaylistMetadata() throws InterruptedException {
        final MediaMetadata2 testMetadata = TestUtils.createMetadata(mContext);
        final AtomicReference<MediaMetadata2> metadataFromCallback = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);
        final ControllerCallback callback = new ControllerCallback() {
            @Override
            public void onPlaylistMetadataChanged(MediaController2 controller,
                    MediaPlaylistAgent playlistAgent, MediaMetadata2 metadata) {
                assertNotNull(testMetadata);
                assertEquals(testMetadata.getMediaId(), metadata.getMediaId());
                metadataFromCallback.set(metadata);
                latch.countDown();
            }
        };
        // Note: Can't use mockito here. See comments at mMockAgent.
        final MediaPlaylistAgent agent = new MediaPlaylistAgent(mContext) {
            @Override
            public MediaMetadata2 getPlaylistMetadata() {
                return testMetadata;
            }
        };
        try (final MediaSession2 session = new MediaSession2.Builder(mContext)
                .setPlayer(mPlayer)
                .setId("testGetPlaylistMetadata")
                .setSessionCallback(sHandlerExecutor, new SessionCallback(mContext) {})
                .setPlaylistAgent(agent)
                .build()) {
            MediaController2 controller = createController(session.getToken(), true, callback);
            agent.notifyPlaylistMetadataChanged();
            assertTrue(latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
            assertEquals(metadataFromCallback.get().getMediaId(),
                    controller.getPlaylistMetadata().getMediaId());
        }
    }

    /**
     * Test whether {@link MediaSession2#setPlaylist(List, MediaMetadata2)} is notified
     * through the
     * {@link ControllerCallback#onPlaylistMetadataChanged(MediaController2, MediaPlaylistAgent, MediaMetadata2)}
     * if the controller doesn't have {@link MediaSession2#COMMAND_CODE_PLAYLIST_GET_LIST} but
     * {@link MediaSession2#COMMAND_CODE_PLAYLIST_GET_LIST_METADATA}.
     */
    @Test
    public void testControllerCallback_onPlaylistMetadataChanged() throws InterruptedException {
        final MediaItem2 item = TestUtils.createMediaItemWithMetadata(mContext);
        final List<MediaItem2> list = TestUtils.createPlaylist(mContext, 2);
        final CountDownLatch latch = new CountDownLatch(2);
        final ControllerCallback callback = new ControllerCallback() {
            @Override
            public void onPlaylistMetadataChanged(MediaController2 controller,
                    MediaPlaylistAgent playlistAgent, MediaMetadata2 metadata) {
                assertNotNull(metadata);
                assertEquals(item.getMediaId(), metadata.getMediaId());
                latch.countDown();
            }
        };
        final SessionCallback sessionCallback = new SessionCallback(mContext) {
            @Override
            public CommandGroup onConnect(MediaSession2 session, ControllerInfo controller) {
                if (Process.myUid() == controller.getUid()) {
                    CommandGroup commands = new CommandGroup(mContext);
                    commands.addCommand(new Command(mContext,
                              MediaSession2.COMMAND_CODE_PLAYLIST_GET_LIST_METADATA));
                    return commands;
                }
                return super.onConnect(session, controller);
            }
        };
        final MediaPlaylistAgent agent = new MediaPlaylistAgent(mContext) {
            @Override
            public MediaMetadata2 getPlaylistMetadata() {
                return item.getMetadata();
            }

            @Override
            public List<MediaItem2> getPlaylist() {
                return list;
            }
        };
        try (final MediaSession2 session = new MediaSession2.Builder(mContext)
                .setPlayer(mPlayer)
                .setId("testControllerCallback_onPlaylistMetadataChanged")
                .setSessionCallback(sHandlerExecutor, sessionCallback)
                .setPlaylistAgent(agent)
                .build()) {
            MediaController2 controller = createController(session.getToken(), true, callback);
            agent.notifyPlaylistMetadataChanged();
            // It also calls onPlaylistMetadataChanged() if it doesn't have permission for getList()
            agent.notifyPlaylistChanged();
            assertTrue(latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        }
    }

    @Test
    public void testAddPlaylistItem() {
        final int testIndex = 12;
        final MediaItem2 testMediaItem = TestUtils.createMediaItemWithMetadata(mContext);
        mController.addPlaylistItem(testIndex, testMediaItem);
        verify(mMockAgent, timeout(TIMEOUT_MS).atLeastOnce())
                .addPlaylistItem(eq(testIndex), argThat((item) -> {
                    assertNotNull(item);
                    assertEquals(testMediaItem.getMediaId(), item.getMediaId());
                    return true;
                }));
    }

    @Test
    public void testRemovePlaylistItem() throws InterruptedException {
        final List<MediaItem2> testList = TestUtils.createPlaylist(mContext, 2);
        doReturn(testList).when(mMockAgent).getPlaylist();

        // Recreate controller for sending removePlaylistItem.
        // It's easier to ensure that MediaController2.getPlaylist() returns the playlist from the
        // agent.
        MediaController2 controller = createController(mSession.getToken());
        controller.removePlaylistItem(controller.getPlaylist().get(0));
        verify(mMockAgent, timeout(TIMEOUT_MS).atLeastOnce())
                .removePlaylistItem(argThat((item) -> {
                    assertEquals(testList.get(0), item);
                    return true;
                }));
    }

    @Test
    public void testReplacePlaylistItem() throws InterruptedException {
        final int testIndex = 12;
        final MediaItem2 testMediaItem = TestUtils.createMediaItemWithMetadata(mContext);
        mController.replacePlaylistItem(testIndex, testMediaItem);
        verify(mMockAgent, timeout(TIMEOUT_MS).atLeastOnce())
                .replacePlaylistItem(eq(testIndex), argThat((item) -> {
                    assertNotNull(item);
                    assertEquals(testMediaItem.getMediaId(), item.getMediaId());
                    return true;
                }));
    }

    @Test
    public void testSkipToPreviousItem() {
        mController.skipToPreviousItem();
        verify(mMockAgent, timeout(TIMEOUT_MS).atLeastOnce()).skipToPreviousItem();
    }

    @Test
    public void testSkipToNextItem() {
        mController.skipToNextItem();
        verify(mMockAgent, timeout(TIMEOUT_MS).atLeastOnce()).skipToNextItem();
    }

    @Test
    public void testSkipToPlaylistItem() throws InterruptedException {
        final List<MediaItem2> testList = TestUtils.createPlaylist(mContext, 2);
        doReturn(testList).when(mMockAgent).getPlaylist();

        MediaController2 controller = createController(mSession.getToken());
        controller.skipToPlaylistItem(controller.getPlaylist().get(0));
        verify(mMockAgent, timeout(TIMEOUT_MS).atLeastOnce())
                .skipToPlaylistItem(argThat((item) -> {
                    assertEquals(testList.get(0), item);
                    return true;
                }));
    }

    /**
     * This also tests {@link ControllerCallback#onShuffleModeChanged(
     * MediaController2, MediaPlaylistAgent, int)}.
     */
    @Test
    public void testGetShuffleMode() throws InterruptedException {
        final int testShuffleMode = MediaPlaylistAgent.SHUFFLE_MODE_GROUP;
        final MediaPlaylistAgent agent = new MediaPlaylistAgent(mContext) {
            @Override
            public int getShuffleMode() {
                return testShuffleMode;
            }
        };
        final CountDownLatch latch = new CountDownLatch(1);
        final ControllerCallback callback = new ControllerCallback() {
            @Override
            public void onShuffleModeChanged(MediaController2 controller,
                    MediaPlaylistAgent playlistAgent, int shuffleMode) {
                assertEquals(testShuffleMode, shuffleMode);
                latch.countDown();
            }
        };
        mSession.updatePlayer(mPlayer, agent, null);
        MediaController2 controller = createController(mSession.getToken(), true, callback);
        agent.notifyShuffleModeChanged();
        assertTrue(latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertEquals(testShuffleMode, controller.getShuffleMode());
    }

    @Test
    public void testSetShuffleMode() {
        final int testShuffleMode = MediaPlaylistAgent.SHUFFLE_MODE_GROUP;
        mController.setShuffleMode(testShuffleMode);
        verify(mMockAgent, timeout(TIMEOUT_MS).atLeastOnce()).setShuffleMode(eq(testShuffleMode));
    }

    /**
     * This also tests {@link ControllerCallback#onRepeatModeChanged(
     * MediaController2, MediaPlaylistAgent, int)}.
     */
    @Test
    public void testGetRepeatMode() throws InterruptedException {
        final int testRepeatMode = MediaPlaylistAgent.REPEAT_MODE_GROUP;
        final MediaPlaylistAgent agent = new MediaPlaylistAgent(mContext) {
            @Override
            public int getRepeatMode() {
                return testRepeatMode;
            }
        };
        final CountDownLatch latch = new CountDownLatch(1);
        final ControllerCallback callback = new ControllerCallback() {
            @Override
            public void onRepeatModeChanged(MediaController2 controller,
                    MediaPlaylistAgent playlistAgent, int repeatMode) {
                assertEquals(testRepeatMode, repeatMode);
                latch.countDown();
            }
        };
        mSession.updatePlayer(mPlayer, agent, null);
        MediaController2 controller = createController(mSession.getToken(), true, callback);
        agent.notifyRepeatModeChanged();
        assertTrue(latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertEquals(testRepeatMode, controller.getRepeatMode());
    }

    @Test
    public void testSetRepeatMode() {
        final int testRepeatMode = MediaPlaylistAgent.REPEAT_MODE_GROUP;
        mController.setRepeatMode(testRepeatMode);
        verify(mMockAgent, timeout(TIMEOUT_MS).atLeastOnce()).setRepeatMode(eq(testRepeatMode));
    }
    
    @Test
    public void testSetVolumeTo() throws Exception {
        final int maxVolume = 100;
        final int currentVolume = 23;
        final int volumeControlType = VolumeProvider2.VOLUME_CONTROL_ABSOLUTE;
        TestVolumeProvider volumeProvider =
                new TestVolumeProvider(mContext, volumeControlType, maxVolume, currentVolume);

        mSession.updatePlayer(new MockPlayer(0), null, volumeProvider);
        final MediaController2 controller = createController(mSession.getToken(), true, null);

        final int targetVolume = 50;
        controller.setVolumeTo(targetVolume, 0 /* flags */);
        assertTrue(volumeProvider.mLatch.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS));
        assertTrue(volumeProvider.mSetVolumeToCalled);
        assertEquals(targetVolume, volumeProvider.mVolume);
    }

    @Test
    public void testAdjustVolume() throws Exception {
        final int maxVolume = 100;
        final int currentVolume = 23;
        final int volumeControlType = VolumeProvider2.VOLUME_CONTROL_ABSOLUTE;
        TestVolumeProvider volumeProvider =
                new TestVolumeProvider(mContext, volumeControlType, maxVolume, currentVolume);

        mSession.updatePlayer(new MockPlayer(0), null, volumeProvider);
        final MediaController2 controller = createController(mSession.getToken(), true, null);

        final int direction = AudioManager.ADJUST_RAISE;
        controller.adjustVolume(direction, 0 /* flags */);
        assertTrue(volumeProvider.mLatch.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS));
        assertTrue(volumeProvider.mAdjustVolumeCalled);
        assertEquals(direction, volumeProvider.mDirection);
    }

    @Test
    public void testGetPackageName() {
        assertEquals(mContext.getPackageName(), mController.getSessionToken().getPackageName());
    }

    @Test
    public void testSendCustomCommand() throws InterruptedException {
        // TODO(jaewan): Need to revisit with the permission.
        final Command testCommand =
                new Command(mContext, MediaSession2.COMMAND_CODE_PLAYBACK_PREPARE);
        final Bundle testArgs = new Bundle();
        testArgs.putString("args", "testSendCustomAction");

        final CountDownLatch latch = new CountDownLatch(1);
        final SessionCallback callback = new SessionCallback(mContext) {
            @Override
            public void onCustomCommand(MediaSession2 session, ControllerInfo controller,
                    Command customCommand, Bundle args, ResultReceiver cb) {
                super.onCustomCommand(session, controller, customCommand, args, cb);
                assertEquals(mContext.getPackageName(), controller.getPackageName());
                assertEquals(testCommand, customCommand);
                assertTrue(TestUtils.equals(testArgs, args));
                assertNull(cb);
                latch.countDown();
            }
        };
        mSession.close();
        mSession = new MediaSession2.Builder(mContext).setPlayer(mPlayer)
                .setSessionCallback(sHandlerExecutor, callback).setId(TAG).build();
        final MediaController2 controller = createController(mSession.getToken());
        controller.sendCustomCommand(testCommand, testArgs, null);
        assertTrue(latch.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testControllerCallback_onConnected() throws InterruptedException {
        // createController() uses controller callback to wait until the controller becomes
        // available.
        MediaController2 controller = createController(mSession.getToken());
        assertNotNull(controller);
    }

    @Test
    public void testControllerCallback_sessionRejects() throws InterruptedException {
        final MediaSession2.SessionCallback sessionCallback = new SessionCallback(mContext) {
            @Override
            public MediaSession2.CommandGroup onConnect(MediaSession2 session,
                    ControllerInfo controller) {
                return null;
            }
        };
        sHandler.postAndSync(() -> {
            mSession.close();
            mSession = new MediaSession2.Builder(mContext).setPlayer(mPlayer)
                    .setSessionCallback(sHandlerExecutor, sessionCallback).build();
        });
        MediaController2 controller =
                createController(mSession.getToken(), false, null);
        assertNotNull(controller);
        waitForConnect(controller, false);
        waitForDisconnect(controller, true);
    }

    @Test
    public void testControllerCallback_releaseSession() throws InterruptedException {
        sHandler.postAndSync(() -> {
            mSession.close();
        });
        waitForDisconnect(mController, true);
    }

    @Test
    public void testControllerCallback_release() throws InterruptedException {
        mController.close();
        waitForDisconnect(mController, true);
    }

    @Test
    public void testPlayFromSearch() throws InterruptedException {
        final String request = "random query";
        final Bundle bundle = new Bundle();
        bundle.putString("key", "value");
        final CountDownLatch latch = new CountDownLatch(1);
        final SessionCallback callback = new SessionCallback(mContext) {
            @Override
            public void onPlayFromSearch(MediaSession2 session, ControllerInfo controller,
                    String query, Bundle extras) {
                super.onPlayFromSearch(session, controller, query, extras);
                assertEquals(mContext.getPackageName(), controller.getPackageName());
                assertEquals(request, query);
                assertTrue(TestUtils.equals(bundle, extras));
                latch.countDown();
            }
        };
        try (MediaSession2 session = new MediaSession2.Builder(mContext)
                .setPlayer(mPlayer)
                .setSessionCallback(sHandlerExecutor, callback)
                .setId("testPlayFromSearch").build()) {
            MediaController2 controller = createController(session.getToken());
            controller.playFromSearch(request, bundle);
            assertTrue(latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        }
    }

    @Test
    public void testPlayFromUri() throws InterruptedException {
        final Uri request = Uri.parse("foo://boo");
        final Bundle bundle = new Bundle();
        bundle.putString("key", "value");
        final CountDownLatch latch = new CountDownLatch(1);
        final SessionCallback callback = new SessionCallback(mContext) {
            @Override
            public void onPlayFromUri(MediaSession2 session, ControllerInfo controller, Uri uri,
                    Bundle extras) {
                assertEquals(mContext.getPackageName(), controller.getPackageName());
                assertEquals(request, uri);
                assertTrue(TestUtils.equals(bundle, extras));
                latch.countDown();
            }
        };
        try (MediaSession2 session = new MediaSession2.Builder(mContext)
                .setPlayer(mPlayer)
                .setSessionCallback(sHandlerExecutor, callback)
                .setId("testPlayFromUri").build()) {
            MediaController2 controller = createController(session.getToken());
            controller.playFromUri(request, bundle);
            assertTrue(latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        }
    }

    @Test
    public void testPlayFromMediaId() throws InterruptedException {
        final String request = "media_id";
        final Bundle bundle = new Bundle();
        bundle.putString("key", "value");
        final CountDownLatch latch = new CountDownLatch(1);
        final SessionCallback callback = new SessionCallback(mContext) {
            @Override
            public void onPlayFromMediaId(MediaSession2 session, ControllerInfo controller,
                    String mediaId, Bundle extras) {
                assertEquals(mContext.getPackageName(), controller.getPackageName());
                assertEquals(request, mediaId);
                assertTrue(TestUtils.equals(bundle, extras));
                latch.countDown();
            }
        };
        try (MediaSession2 session = new MediaSession2.Builder(mContext)
                .setPlayer(mPlayer)
                .setSessionCallback(sHandlerExecutor, callback)
                .setId("testPlayFromMediaId").build()) {
            MediaController2 controller = createController(session.getToken());
            controller.playFromMediaId(request, bundle);
            assertTrue(latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        }
    }

    @Test
    public void testPrepareFromSearch() throws InterruptedException {
        final String request = "random query";
        final Bundle bundle = new Bundle();
        bundle.putString("key", "value");
        final CountDownLatch latch = new CountDownLatch(1);
        final SessionCallback callback = new SessionCallback(mContext) {
            @Override
            public void onPrepareFromSearch(MediaSession2 session, ControllerInfo controller,
                    String query, Bundle extras) {
                assertEquals(mContext.getPackageName(), controller.getPackageName());
                assertEquals(request, query);
                assertTrue(TestUtils.equals(bundle, extras));
                latch.countDown();
            }
        };
        try (MediaSession2 session = new MediaSession2.Builder(mContext)
                .setPlayer(mPlayer)
                .setSessionCallback(sHandlerExecutor, callback)
                .setId("testPrepareFromSearch").build()) {
            MediaController2 controller = createController(session.getToken());
            controller.prepareFromSearch(request, bundle);
            assertTrue(latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        }
    }

    @Test
    public void testPrepareFromUri() throws InterruptedException {
        final Uri request = Uri.parse("foo://boo");
        final Bundle bundle = new Bundle();
        bundle.putString("key", "value");
        final CountDownLatch latch = new CountDownLatch(1);
        final SessionCallback callback = new SessionCallback(mContext) {
            @Override
            public void onPrepareFromUri(MediaSession2 session, ControllerInfo controller, Uri uri,
                    Bundle extras) {
                assertEquals(mContext.getPackageName(), controller.getPackageName());
                assertEquals(request, uri);
                assertTrue(TestUtils.equals(bundle, extras));
                latch.countDown();
            }
        };
        try (MediaSession2 session = new MediaSession2.Builder(mContext)
                .setPlayer(mPlayer)
                .setSessionCallback(sHandlerExecutor, callback)
                .setId("testPrepareFromUri").build()) {
            MediaController2 controller = createController(session.getToken());
            controller.prepareFromUri(request, bundle);
            assertTrue(latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        }
    }

    @Test
    public void testPrepareFromMediaId() throws InterruptedException {
        final String request = "media_id";
        final Bundle bundle = new Bundle();
        bundle.putString("key", "value");
        final CountDownLatch latch = new CountDownLatch(1);
        final SessionCallback callback = new SessionCallback(mContext) {
            @Override
            public void onPrepareFromMediaId(MediaSession2 session, ControllerInfo controller,
                    String mediaId, Bundle extras) {
                assertEquals(mContext.getPackageName(), controller.getPackageName());
                assertEquals(request, mediaId);
                assertTrue(TestUtils.equals(bundle, extras));
                latch.countDown();
            }
        };
        try (MediaSession2 session = new MediaSession2.Builder(mContext)
                .setPlayer(mPlayer)
                .setSessionCallback(sHandlerExecutor, callback)
                .setId("testPrepareFromMediaId").build()) {
            MediaController2 controller = createController(session.getToken());
            controller.prepareFromMediaId(request, bundle);
            assertTrue(latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        }
    }

    @Test
    public void testSetRating() throws InterruptedException {
        final int ratingType = Rating2.RATING_5_STARS;
        final float ratingValue = 3.5f;
        final Rating2 rating = Rating2.newStarRating(mContext, ratingType, ratingValue);
        final String mediaId = "media_id";

        final CountDownLatch latch = new CountDownLatch(1);
        final SessionCallback callback = new SessionCallback(mContext) {
            @Override
            public void onSetRating(MediaSession2 session, ControllerInfo controller,
                    String mediaIdOut, Rating2 ratingOut) {
                assertEquals(mContext.getPackageName(), controller.getPackageName());
                assertEquals(mediaId, mediaIdOut);
                assertEquals(rating, ratingOut);
                latch.countDown();
            }
        };

        try (MediaSession2 session = new MediaSession2.Builder(mContext)
                .setPlayer(mPlayer)
                .setSessionCallback(sHandlerExecutor, callback)
                .setId("testSetRating").build()) {
            MediaController2 controller = createController(session.getToken());
            controller.setRating(mediaId, rating);
            assertTrue(latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        }
    }

    @Test
    public void testIsConnected() throws InterruptedException {
        assertTrue(mController.isConnected());
        sHandler.postAndSync(()->{
            mSession.close();
        });
        // postAndSync() to wait until the disconnection is propagated.
        sHandler.postAndSync(()->{
            assertFalse(mController.isConnected());
        });
    }

    /**
     * Test potential deadlock for calls between controller and session.
     */
    @Test
    public void testDeadlock() throws InterruptedException {
        sHandler.postAndSync(() -> {
            mSession.close();
            mSession = null;
        });

        // Two more threads are needed not to block test thread nor test wide thread (sHandler).
        final HandlerThread sessionThread = new HandlerThread("testDeadlock_session");
        final HandlerThread testThread = new HandlerThread("testDeadlock_test");
        sessionThread.start();
        testThread.start();
        final SyncHandler sessionHandler = new SyncHandler(sessionThread.getLooper());
        final Handler testHandler = new Handler(testThread.getLooper());
        final CountDownLatch latch = new CountDownLatch(1);
        try {
            final MockPlayer player = new MockPlayer(0);
            sessionHandler.postAndSync(() -> {
                mSession = new MediaSession2.Builder(mContext)
                        .setPlayer(mPlayer)
                        .setSessionCallback(sHandlerExecutor, new SessionCallback(mContext) {})
                        .setId("testDeadlock").build();
            });
            final MediaController2 controller = createController(mSession.getToken());
            testHandler.post(() -> {
                final int state = MediaPlayerBase.PLAYER_STATE_ERROR;
                for (int i = 0; i < 100; i++) {
                    // triggers call from session to controller.
                    player.notifyPlaybackState(state);
                    // triggers call from controller to session.
                    controller.play();

                    // Repeat above
                    player.notifyPlaybackState(state);
                    controller.pause();
                    player.notifyPlaybackState(state);
                    controller.stop();
                    player.notifyPlaybackState(state);
                    controller.skipToNextItem();
                    player.notifyPlaybackState(state);
                    controller.skipToPreviousItem();
                }
                // This may hang if deadlock happens.
                latch.countDown();
            });
            assertTrue(latch.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS));
        } finally {
            if (mSession != null) {
                sessionHandler.postAndSync(() -> {
                    // Clean up here because sessionHandler will be removed afterwards.
                    mSession.close();
                    mSession = null;
                });
            }
            if (sessionThread != null) {
                sessionThread.quitSafely();
            }
            if (testThread != null) {
                testThread.quitSafely();
            }
        }
    }

    @Test
    public void testGetServiceToken() {
        SessionToken2 token = TestUtils.getServiceToken(mContext, MockMediaSessionService2.ID);
        assertNotNull(token);
        assertEquals(mContext.getPackageName(), token.getPackageName());
        assertEquals(MockMediaSessionService2.ID, token.getId());
        assertEquals(SessionToken2.TYPE_SESSION_SERVICE, token.getType());
    }

    @Test
    public void testConnectToService_sessionService() throws InterruptedException {
        testConnectToService(MockMediaSessionService2.ID);
    }

    @Ignore
    @Test
    public void testConnectToService_libraryService() throws InterruptedException {
        testConnectToService(MockMediaLibraryService2.ID);
    }

    public void testConnectToService(String id) throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        final SessionCallback sessionCallback = new SessionCallback(mContext) {
            @Override
            public CommandGroup onConnect(@NonNull MediaSession2 session,
                    @NonNull ControllerInfo controller) {
                if (Process.myUid() == controller.getUid()) {
                    if (mSession != null) {
                        mSession.close();
                    }
                    mSession = session;
                    mPlayer = (MockPlayer) session.getPlayer();
                    assertEquals(mContext.getPackageName(), controller.getPackageName());
                    assertFalse(controller.isTrusted());
                    latch.countDown();
                }
                return super.onConnect(session, controller);
            }
        };
        TestServiceRegistry.getInstance().setSessionCallback(sessionCallback);

        mController = createController(TestUtils.getServiceToken(mContext, id));
        assertTrue(latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));

        // Test command from controller to session service
        mController.play();
        assertTrue(mPlayer.mCountDownLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertTrue(mPlayer.mPlayCalled);

        // Test command from session service to controller
        // TODO(jaewan): Add equivalent tests again
        /*
        final CountDownLatch latch = new CountDownLatch(1);
        mController.registerPlayerEventCallback((state) -> {
            assertNotNull(state);
            assertEquals(PlaybackState.STATE_REWINDING, state.getState());
            latch.countDown();
        }, sHandler);
        mPlayer.notifyPlaybackState(
                TestUtils.createPlaybackState(PlaybackState.STATE_REWINDING));
        assertTrue(latch.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS));
        */
    }

    @Test
    public void testControllerAfterSessionIsGone_session() throws InterruptedException {
        testControllerAfterSessionIsGone(mSession.getToken().getId());
    }

    // TODO(jaewan): Re-enable this test
    @Ignore
    @Test
    public void testControllerAfterSessionIsGone_sessionService() throws InterruptedException {
        /*
        connectToService(TestUtils.getServiceToken(mContext, MockMediaSessionService2.ID));
        testControllerAfterSessionIsGone(MockMediaSessionService2.ID);
        */
    }

    @Test
    public void testClose_beforeConnected() throws InterruptedException {
        MediaController2 controller =
                createController(mSession.getToken(), false, null);
        controller.close();
    }

    @Test
    public void testClose_twice() {
        mController.close();
        mController.close();
    }

    @Test
    public void testClose_session() throws InterruptedException {
        final String id = mSession.getToken().getId();
        mController.close();
        // close is done immediately for session.
        testNoInteraction();

        // Test whether the controller is notified about later close of the session or
        // re-creation.
        testControllerAfterSessionIsGone(id);
    }

    @Test
    public void testClose_sessionService() throws InterruptedException {
        testCloseFromService(MockMediaSessionService2.ID);
    }

    @Test
    public void testClose_libraryService() throws InterruptedException {
        testCloseFromService(MockMediaLibraryService2.ID);
    }

    private void testCloseFromService(String id) throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        TestServiceRegistry.getInstance().setSessionServiceCallback(new SessionServiceCallback() {
            @Override
            public void onDestroyed() {
                latch.countDown();
            }
        });
        mController = createController(TestUtils.getServiceToken(mContext, id));
        mController.close();
        // Wait until close triggers onDestroy() of the session service.
        assertTrue(latch.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS));
        assertNull(TestServiceRegistry.getInstance().getServiceInstance());
        testNoInteraction();

        // Test whether the controller is notified about later close of the session or
        // re-creation.
        testControllerAfterSessionIsGone(id);
    }

    private void testControllerAfterSessionIsGone(final String id) throws InterruptedException {
        sHandler.postAndSync(() -> {
            // TODO(jaewan): Use Session.close later when we add the API.
            mSession.close();
        });
        waitForDisconnect(mController, true);
        testNoInteraction();

        // Ensure that the controller cannot use newly create session with the same ID.
        sHandler.postAndSync(() -> {
            // Recreated session has different session stub, so previously created controller
            // shouldn't be available.
            mSession = new MediaSession2.Builder(mContext)
                    .setPlayer(mPlayer)
                    .setSessionCallback(sHandlerExecutor, new SessionCallback(mContext) {})
                    .setId(id).build();
        });
        testNoInteraction();
    }

    private void testNoInteraction() throws InterruptedException {
        // TODO: Uncomment
        /*
        final CountDownLatch latch = new CountDownLatch(1);
        final PlayerEventCallback callback = new PlayerEventCallback() {
            @Override
            public void onPlaybackStateChanged(PlaybackState2 state) {
                fail("Controller shouldn't be notified about change in session after the close.");
                latch.countDown();
            }
        };
        */

        // TODO(jaewan): Add equivalent tests again
        /*
        mController.registerPlayerEventCallback(playbackListener, sHandler);
        mPlayer.notifyPlaybackState(TestUtils.createPlaybackState(PlaybackState.STATE_BUFFERING));
        assertFalse(latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        mController.unregisterPlayerEventCallback(playbackListener);
        */
    }

    // TODO(jaewan): Add  test for service connect rejection, when we differentiate session
    //               active/inactive and connection accept/refuse

    class TestVolumeProvider extends VolumeProvider2 {
        final CountDownLatch mLatch = new CountDownLatch(1);
        boolean mSetVolumeToCalled;
        boolean mAdjustVolumeCalled;
        int mVolume;
        int mDirection;

        public TestVolumeProvider(Context context, int controlType, int maxVolume,
                int currentVolume) {
            super(context, controlType, maxVolume, currentVolume);
        }

        @Override
        public void onSetVolumeTo(int volume) {
            mSetVolumeToCalled = true;
            mVolume = volume;
            mLatch.countDown();
        }

        @Override
        public void onAdjustVolume(int direction) {
            mAdjustVolumeCalled = true;
            mDirection = direction;
            mLatch.countDown();
        }
    }
}
