/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.server.am;

import android.content.ComponentName;
import android.server.am.component.ComponentsBase;

public class Components extends ComponentsBase {
    public static final ComponentName ALT_LAUNCHING_ACTIVITY = component("AltLaunchingActivity");
    public static final ComponentName ALWAYS_FOCUSABLE_PIP_ACTIVITY =
            component("AlwaysFocusablePipActivity");
    public static final ComponentName ANIMATION_TEST_ACTIVITY = component("AnimationTestActivity");
    public static final ComponentName ASSISTANT_ACTIVITY = component("AssistantActivity");
    public static final ComponentName BOTTOM_ACTIVITY = component("BottomActivity");
    public static final ComponentName BOTTOM_LEFT_LAYOUT_ACTIVITY =
            component("BottomLeftLayoutActivity");
    public static final ComponentName BOTTOM_RIGHT_LAYOUT_ACTIVITY =
            component("BottomRightLayoutActivity");
    public static final ComponentName BROADCAST_RECEIVER_ACTIVITY =
            component("BroadcastReceiverActivity");
    public static final ComponentName DIALOG_WHEN_LARGE_ACTIVITY =
            component("DialogWhenLargeActivity");
    public static final ComponentName DISMISS_KEYGUARD_ACTIVITY =
            component("DismissKeyguardActivity");
    public static final ComponentName DISMISS_KEYGUARD_METHOD_ACTIVITY =
            component("DismissKeyguardMethodActivity");
    public static final ComponentName DOCKED_ACTIVITY = component("DockedActivity");
    public static final ComponentName ENTRY_POINT_ALIAS_ACTIVITY =
            component("EntryPointAliasActivity");
    public static final ComponentName FONT_SCALE_ACTIVITY = component("FontScaleActivity");
    public static final ComponentName FONT_SCALE_NO_RELAUNCH_ACTIVITY =
            component("FontScaleNoRelaunchActivity");
    public static final ComponentName FREEFORM_ACTIVITY = component("FreeformActivity");
    public static final ComponentName KEYGUARD_LOCK_ACTIVITY = component("KeyguardLockActivity");
    public static final ComponentName LANDSCAPE_ORIENTATION_ACTIVITY =
            component("LandscapeOrientationActivity");
    public static final ComponentName LAUNCH_ASSISTANT_ACTIVITY_FROM_SESSION =
            component("LaunchAssistantActivityFromSession");
    public static final ComponentName LAUNCH_ASSISTANT_ACTIVITY_INTO_STACK  =
            component("LaunchAssistantActivityIntoAssistantStack");
    public static final ComponentName LAUNCH_PIP_ON_PIP_ACTIVITY =
            component("LaunchPipOnPipActivity");
    public static final ComponentName LAUNCHING_ACTIVITY = component("LaunchingActivity");
    public static final ComponentName LOG_CONFIGURATION_ACTIVITY =
            component("LogConfigurationActivity");
    public static final ComponentName MOVE_TASK_TO_BACK_ACTIVITY =
            component("MoveTaskToBackActivity");
    public static final ComponentName NIGHT_MODE_ACTIVITY = component("NightModeActivity");
    public static final ComponentName NO_HISTORY_ACTIVITY = component("NoHistoryActivity");
    public static final ComponentName NO_RELAUNCH_ACTIVITY = component("NoRelaunchActivity");
    public static final ComponentName NON_RESIZEABLE_ACTIVITY = component("NonResizeableActivity");
    public static final ComponentName PIP_ACTIVITY = component("PipActivity");
    public static final ComponentName PORTRAIT_ORIENTATION_ACTIVITY =
            component("PortraitOrientationActivity");
    public static final ComponentName RESIZEABLE_ACTIVITY = component("ResizeableActivity");
    public static final ComponentName SHOW_WHEN_LOCKED_ACTIVITY =
            component("ShowWhenLockedActivity");
    public static final ComponentName SHOW_WHEN_LOCKED_ATTR_ACTIVITY =
            component("ShowWhenLockedAttrActivity");
    public static final ComponentName SHOW_WHEN_LOCKED_ATTR_REMOVE_ATTR_ACTIVITY =
            component("ShowWhenLockedAttrRemoveAttrActivity");
    public static final ComponentName SHOW_WHEN_LOCKED_DIALOG_ACTIVITY =
            component("ShowWhenLockedDialogActivity");
    public static final ComponentName SHOW_WHEN_LOCKED_TRANSLUCENT_ACTIVITY =
            component("ShowWhenLockedTranslucentActivity");
    public static final ComponentName SHOW_WHEN_LOCKED_WITH_DIALOG_ACTIVITY =
            component("ShowWhenLockedWithDialogActivity");
    public static final ComponentName SINGLE_INSTANCE_ACTIVITY =
            component("SingleInstanceActivity");
    public static final ComponentName SINGLE_TASK_ACTIVITY = component("SingleTaskActivity");
    public static final ComponentName SLOW_CREATE_ACTIVITY = component("SlowCreateActivity");
    public static final ComponentName SPLASHSCREEN_ACTIVITY = component("SplashscreenActivity");
    public static final ComponentName SWIPE_REFRESH_ACTIVITY = component("SwipeRefreshActivity");
    public static final ComponentName TEST_ACTIVITY = component("TestActivity");
    public static final ComponentName TOP_ACTIVITY = component("TopActivity");
    public static final ComponentName TOP_LEFT_LAYOUT_ACTIVITY = component("TopLeftLayoutActivity");
    public static final ComponentName TOP_RIGHT_LAYOUT_ACTIVITY =
            component("TopRightLayoutActivity");
    public static final ComponentName TRANSLUCENT_ACTIVITY = component("TranslucentActivity");
    public static final ComponentName TRANSLUCENT_ASSISTANT_ACTIVITY =
            component("TranslucentAssistantActivity");
    public static final ComponentName TRANSLUCENT_TOP_ACTIVITY =
            component("TranslucentTopActivity");
    public static final ComponentName TURN_SCREEN_ON_ACTIVITY = component("TurnScreenOnActivity");
    public static final ComponentName TURN_SCREEN_ON_ATTR_ACTIVITY =
            component("TurnScreenOnAttrActivity");
    public static final ComponentName TURN_SCREEN_ON_ATTR_DISMISS_KEYGUARD_ACTIVITY =
            component("TurnScreenOnAttrDismissKeyguardActivity");
    public static final ComponentName TURN_SCREEN_ON_ATTR_REMOVE_ATTR_ACTIVITY =
            component("TurnScreenOnAttrRemoveAttrActivity");
    public static final ComponentName TURN_SCREEN_ON_DISMISS_KEYGUARD_ACTIVITY =
            component("TurnScreenOnDismissKeyguardActivity");
    public static final ComponentName TURN_SCREEN_ON_SHOW_ON_LOCK_ACTIVITY =
            component("TurnScreenOnShowOnLockActivity");
    public static final ComponentName TURN_SCREEN_ON_SINGLE_TASK_ACTIVITY =
            component("TurnScreenOnSingleTaskActivity");
    public static final ComponentName TURN_SCREEN_ON_WITH_RELAYOUT_ACTIVITY =
            component("TurnScreenOnWithRelayoutActivity");
    public static final ComponentName VIRTUAL_DISPLAY_ACTIVITY =
            component("VirtualDisplayActivity");
    public static final ComponentName VR_TEST_ACTIVITY = component("VrTestActivity");
    public static final ComponentName WALLPAPAER_ACTIVITY = component("WallpaperActivity");

    public static final ComponentName ASSISTANT_VOICE_INTERACTION_SERVICE =
            component("AssistantVoiceInteractionService");

    public static final ComponentName LAUNCH_BROADCAST_RECEIVER =
            component("LaunchBroadcastReceiver");
    public static final String LAUNCH_BROADCAST_ACTION =
            getPackageName() + ".LAUNCH_BROADCAST_ACTION";

    /**
     * Action and extra key constants for {@link #TEST_ACTIVITY}.
     *
     * TODO(b/73346885): These constants should be in {@link android.server.am.TestActivity} once
     * the activity is moved to test APK.
     */
    public static class TestActivity {
        // Finishes the activity
        public static final String TEST_ACTIVITY_ACTION_FINISH_SELF =
                TestActivity.class.getName() + ".finish_self";
        // Sets the fixed orientation (can be one of {@link ActivityInfo.ScreenOrientation}
        public static final String EXTRA_FIXED_ORIENTATION = "fixed_orientation";
    }

    /**
     * Extra key constants for {@link #LAUNCH_ASSISTANT_ACTIVITY_INTO_STACK} and
     * {@link #LAUNCH_ASSISTANT_ACTIVITY_FROM_SESSION}.
     *
     * TODO(b/73346885): These constants should be in {@link android.server.am.AssistantActivity}
     * once the activity is moved to test APK.
     */
    public static class AssistantActivity {
        // Launches the given activity in onResume
        public static final String EXTRA_LAUNCH_NEW_TASK = "launch_new_task";
        // Finishes this activity in onResume, this happens after EXTRA_LAUNCH_NEW_TASK
        public static final String EXTRA_FINISH_SELF = "finish_self";
        // Attempts to enter picture-in-picture in onResume
        public static final String EXTRA_ENTER_PIP = "enter_pip";
        // Display on which Assistant runs
        public static final String EXTRA_ASSISTANT_DISPLAY_ID = "assistant_display_id";
    }

    /**
     * Extra key constants for {@link #LAUNCH_ASSISTANT_ACTIVITY_INTO_STACK}.
     *
     * TODO(b/73346885): These constants should be in
     * {@link android.server.am.LaunchAssistantActivityIntoAssistantStack} once the activity is
     * moved to test APK.
     */
    public static class LaunchAssistantActivityIntoAssistantStack {
        // Launches the translucent assist activity
        public static final String EXTRA_IS_TRANSLUCENT = "is_translucent";
    }

    private static ComponentName component(String className) {
        return component(Components.class, className);
    }

    private static String getPackageName() {
        return getPackageName(Components.class);
    }
}
