/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.server.cts;

import android.providers.settings.SettingProto;
import android.providers.settings.SettingsOperationProto;
import android.providers.settings.SettingsServiceDumpProto;
import android.providers.settings.UserSettingsProto;

import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.log.LogUtil.CLog;
import com.google.protobuf.GeneratedMessage;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Test to check that the settings service properly outputs its dump state.
 */
public class SettingsIncidentTest extends ProtoDumpTestCase {

    /**
     * Test that there are some secure/system settings for the user and there are some global
     * settings.
     *
     * @throws Exception
     */
    public void testBasicStructure() throws Exception {
        SettingsServiceDumpProto dump = getDump(SettingsServiceDumpProto.parser(),
                "dumpsys settings --proto");

        verifySettingsServiceDumpProto(dump, PRIVACY_NONE);
    }

    static void verifySettingsServiceDumpProto(SettingsServiceDumpProto dump, final int filterLevel) throws Exception {
        assertTrue(dump.getUserSettingsCount() > 0);

        UserSettingsProto userSettings = dump.getUserSettings(0);
        assertEquals(0, userSettings.getUserId());

        CLog.logAndDisplay(LogLevel.INFO, "#*#*#*#*#*#*#*#*#*#*# SECURE #*#*#*#*#*#*#*#*#*#*#");
        verifySettings(userSettings.getSecureSettings());
        CLog.logAndDisplay(LogLevel.INFO, "#*#*#*#*#*#*#*#*#*#*# SYSTEM #*#*#*#*#*#*#*#*#*#*#");
        verifySettings(userSettings.getSystemSettings());

        CLog.logAndDisplay(LogLevel.INFO, "#*#*#*#*#*#*#*#*#*#*# GLOBAL #*#*#*#*#*#*#*#*#*#*#");
        verifySettings(dump.getGlobalSettings());
    }

    private static void verifySettings(GeneratedMessage settings) throws Exception {
        verifySettings(getSettingProtos(settings));

        final List<SettingsOperationProto> ops = invoke(settings, "getHistoricalOperationsList");
        for (SettingsOperationProto op : ops) {
            assertTrue(op.getTimestamp() >= 0);
            assertNotNull(op.getOperation());
            // setting is optional
        }
    }

    private static List<SettingProto> getSettingProtos(GeneratedMessage settingsProto) {
        CLog.d("Checking out class: " + settingsProto.getClass());

        Stream<SettingProto> settings = Arrays.stream(settingsProto.getClass().getDeclaredMethods())
                .filter((method) ->
                        method.getName().startsWith("get")
                                && !method.getName().endsWith("OrBuilder")
                                && method.getParameterCount() == 0
                                && !Modifier.isStatic(method.getModifiers())
                                && method.getReturnType() == SettingProto.class)
                .map((method) -> (SettingProto) invoke(method, settingsProto));

        // Get fields in the submessages.
        Stream<SettingProto> subSettings = Arrays.stream(settingsProto.getClass().getDeclaredMethods())
                .filter((method) ->
                        method.getName().startsWith("get")
                                && !method.getName().endsWith("OrBuilder")
                                && method.getParameterCount() == 0
                                && !Modifier.isStatic(method.getModifiers())
                                && method.getReturnType() != SettingProto.class
                                && GeneratedMessage.class.isAssignableFrom(method.getReturnType())
                                && method.getReturnType() != settingsProto.getClass())
                .map((method) -> getSettingProtos((GeneratedMessage) invoke(method, settingsProto)))
                // getSettingsProtos returns List<>, so flatten all of them into one.
                .flatMap(Collection::stream);

        return Stream.concat(settings, subSettings).collect(Collectors.toList());
    }

    private static <T> T invoke(Method method, Object instance, Object... args) {
        method.setAccessible(true);
        try {
            return (T) method.invoke(instance, args);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static <T> T invoke(GeneratedMessage instance, String methodName, Object... args)
            throws Exception {
        final Class<?>[] inputParamTypes = Arrays.stream(args)
                .map((arg) -> toPrimitive(arg.getClass()))
                .toArray(Class[]::new);
        return invoke(
                instance.getClass().getDeclaredMethod(methodName, inputParamTypes),
                instance, args);
    }

    private static Class<?> toPrimitive(Class<?> c) {
        return c == Integer.class ? int.class : c;
    }

    private static void verifySettings(List<SettingProto> settings) throws Exception {
        assertFalse(settings.isEmpty());

        CLog.d("Field count: " + settings.size());
        for (SettingProto setting : settings) {
            try {
                final String id = setting.getId();
                if (!id.isEmpty()) {
                    // _ID has to be a long converted to a String
                    Long.parseLong(id);
                }
                assertNotNull(setting.getName());
                // pkg is optional
                // value can be anything
                // default can be anything
                // default from system reported only if optional default present
            } catch (Throwable e) {
                throw new AssertionError("Failed for setting " + setting, e);
            }
        }
    }
}

