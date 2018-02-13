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
package android.cts.statsd.alert;

import android.cts.statsd.atom.AtomTestCase;

import com.android.internal.os.StatsdConfigProto;
import com.android.internal.os.StatsdConfigProto.Alert;
import com.android.internal.os.StatsdConfigProto.CountMetric;
import com.android.internal.os.StatsdConfigProto.DurationMetric;
import com.android.internal.os.StatsdConfigProto.FieldFilter;
import com.android.internal.os.StatsdConfigProto.FieldMatcher;
import com.android.internal.os.StatsdConfigProto.GaugeMetric;
import com.android.internal.os.StatsdConfigProto.IncidentdDetails;
import com.android.internal.os.StatsdConfigProto.StatsdConfig;
import com.android.internal.os.StatsdConfigProto.Subscription;
import com.android.internal.os.StatsdConfigProto.TimeUnit;
import com.android.internal.os.StatsdConfigProto.ValueMetric;
import com.android.os.AtomsProto.AnomalyDetected;
import com.android.os.AtomsProto.AppHook;
import com.android.os.AtomsProto.Atom;
import com.android.os.StatsLog.EventMetricData;
import com.android.tradefed.log.LogUtil.CLog;

import java.util.List;

/**
 * Statsd Anomaly Detection tests.
 */
public class AnomalyDetectionTests extends AtomTestCase {

    private static final String TAG = "Statsd.AnomalyDetectionTests";

    private static final boolean TESTS_ENABLED = false;
    private static final boolean INCIDENTD_TESTS_ENABLED = true;

    // Config constants
    private static final int APP_HOOK_MATCH_START_ID = 1;
    private static final int APP_HOOK_MATCH_STOP_ID = 2;
    private static final int METRIC_ID = 8;
    private static final int ALERT_ID = 11;
    private static final int SUBSCRIPTION_ID_INCIDENTD = 41;
    private static final int ANOMALY_DETECT_MATCH_ID = 10;
    private static final int ANOMALY_EVENT_ID = 101;
    private static final int APP_HOOK_UID = 0;
    private static final int INCIDENTD_SECTION = -1;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        if (!TESTS_ENABLED) {
            CLog.w(TAG, TAG + " tests are disabled by a flag. Change flag to true to run.");
        }
        if (!INCIDENTD_TESTS_ENABLED) {
            CLog.w(TAG, TAG + " anomaly tests are disabled by a flag. Change flag to true to run");
        }
    }

    // Tests that anomaly detection for count works.
    // Also tests that anomaly detection works when spanning multiple buckets.
    public void testCountAnomalyDetection() throws Exception {
        if (!TESTS_ENABLED) return;
        StatsdConfig.Builder config = getBaseConfig(10, // num buckets
                                                    20, // refractory period (seconds)
                                                    2) // threshold: > 2 counts
                .addCountMetric(CountMetric.newBuilder()
                        .setId(METRIC_ID)
                        .setWhat(APP_HOOK_MATCH_START_ID)
                        .setBucket(TimeUnit.CTS) // 1 second
                        // Slice by label
                        .setDimensionsInWhat(FieldMatcher.newBuilder()
                                .setField(Atom.APP_HOOK_FIELD_NUMBER)
                                .addChild(FieldMatcher.newBuilder()
                                        .setField(AppHook.LABEL_FIELD_NUMBER)
                                )
                        )
                );
        uploadConfig(config);

        String markTime = getCurrentLogcatDate();
        doAppHookStart(6); // count(label=6) -> 1 (not an anomaly, since not "greater than 2")
        Thread.sleep(500);
        if (INCIDENTD_TESTS_ENABLED) assertFalse("Incident", didIncidentdFireSince(markTime));
        assertEquals("Premature anomaly", 0, getEventMetricDataList().size());

        doAppHookStart(6); // count(label=6) -> 2 (not an anomaly, since not "greater than 2")
        Thread.sleep(500);
        if (INCIDENTD_TESTS_ENABLED) assertFalse("Incident", didIncidentdFireSince(markTime));
        assertEquals("Premature anomaly", 0, getEventMetricDataList().size());

        doAppHookStart(12); // count(label=12) -> 1 (not an anomaly, since not "greater than 2")
        Thread.sleep(1000);
        if (INCIDENTD_TESTS_ENABLED) assertFalse("Incident", didIncidentdFireSince(markTime));
        assertEquals("Premature anomaly", 0, getEventMetricDataList().size());

        doAppHookStart(6); // count(label=6) -> 3 (anomaly, since "greater than 2"!)
        Thread.sleep(1000);

        if (INCIDENTD_TESTS_ENABLED) assertTrue("No incident", didIncidentdFireSince(markTime));
        List<EventMetricData> data = getEventMetricDataList();
        assertEquals("Expected 1 anomaly", 1, data.size());
        AnomalyDetected a = data.get(0).getAtom().getAnomalyDetected();
        assertEquals("Wrong alert_id", ALERT_ID, a.getAlertId());
    }

    // Tests that anomaly detection for duration works.
    // Also tests that refractory periods in anomaly detection work.
    public void testDurationAnomalyDetection() throws Exception {
        if (!TESTS_ENABLED) return;
        final int APP_HOOK_IS_ON_PREDICATE = 1423;
        StatsdConfig.Builder config =
                getBaseConfig(17, // num buckets
                              17, // refractory period (seconds)
                              10_000_000_000L) // threshold: > 10 seconds in nanoseconds
                .addDurationMetric(DurationMetric.newBuilder()
                        .setId(METRIC_ID)
                        .setWhat(APP_HOOK_IS_ON_PREDICATE) // the Predicate added below
                        .setAggregationType(DurationMetric.AggregationType.SUM)
                        .setBucket(TimeUnit.CTS) // 1 second
                )
                .addPredicate(StatsdConfigProto.Predicate.newBuilder()
                        .setId(APP_HOOK_IS_ON_PREDICATE)
                        .setSimplePredicate(StatsdConfigProto.SimplePredicate.newBuilder()
                                .setStart(APP_HOOK_MATCH_START_ID)
                                .setStop(APP_HOOK_MATCH_STOP_ID)
                        )
                );
        uploadConfig(config);

        // Since timing is crucial and checking logcat for incidentd is slow, we don't test for it.

        // We prevent the device from sleeping through the anomaly alarm to avoid extra waiting.
        // This assumes (as per CTS requirements) that screen Stay Awake is on.
        turnScreenOn();

        // Test that alarm doesn't fire early.
        String markTime = getCurrentLogcatDate();
        doAppHookStart(1);
        Thread.sleep(6_000);
        assertEquals("Premature anomaly,", 0, getEventMetricDataList().size());

        doAppHookStop(1);
        Thread.sleep(4_000);
        assertEquals("Premature anomaly,", 0, getEventMetricDataList().size());

        // Test that alarm does fire when it is supposed to (after 4s, plus up to 5s alarm delay).
        doAppHookStart(1);
        Thread.sleep(9_000);
        List<EventMetricData> data = getEventMetricDataList();
        assertEquals("Expected an anomaly,", 1, data.size());
        assertEquals(ALERT_ID, data.get(0).getAtom().getAnomalyDetected().getAlertId());

        // Now test that the refractory period is obeyed.
        markTime = getCurrentLogcatDate();
        doAppHookStop(1);
        doAppHookStart(1);
        Thread.sleep(3_000);
        // NB: the previous getEventMetricDataList also removes the report, so it is back to 0.
        assertEquals("Expected only 1 anomaly,", 0, getEventMetricDataList().size());

        // Test that detection works again after refractory period finishes.
        doAppHookStop(1);
        Thread.sleep(8_000);
        doAppHookStart(1);
        Thread.sleep(10_000);
        // We can do an incidentd test now that all the timing issues are done.
        if (INCIDENTD_TESTS_ENABLED) assertTrue("No incident", didIncidentdFireSince(markTime));
        data = getEventMetricDataList();
        assertEquals("Expected another anomaly,", 1, data.size());
        assertEquals(ALERT_ID, data.get(0).getAtom().getAnomalyDetected().getAlertId());

        doAppHookStop(1);
        turnScreenOff();
    }

    // Tests that anomaly detection for duration works even when the alarm fires too late.
    public void testDurationAnomalyDetectionForLateAlarms() throws Exception {
        if (!TESTS_ENABLED) return;
        final int APP_HOOK_IS_ON_PREDICATE = 1423;
        StatsdConfig.Builder config =
                getBaseConfig(50, // num buckets
                        0, // refractory period (seconds)
                        6_000_000_000L) // threshold: > 6 seconds in nanoseconds
                        .addDurationMetric(DurationMetric.newBuilder()
                                .setId(METRIC_ID)
                                .setWhat(APP_HOOK_IS_ON_PREDICATE) // the Predicate added below
                                .setAggregationType(DurationMetric.AggregationType.SUM)
                                .setBucket(TimeUnit.CTS) // 1 second
                        )
                        .addPredicate(StatsdConfigProto.Predicate.newBuilder()
                                .setId(APP_HOOK_IS_ON_PREDICATE)
                                .setSimplePredicate(StatsdConfigProto.SimplePredicate.newBuilder()
                                        .setStart(APP_HOOK_MATCH_START_ID)
                                        .setStop(APP_HOOK_MATCH_STOP_ID)
                                )
                        );
        uploadConfig(config);

        // We prevent the device from sleeping through the anomaly alarm to avoid extra waiting.
        // This assumes (as per CTS requirements) that screen Stay Awake is on.
        turnScreenOn();

        doAppHookStart(1);
        Thread.sleep(5_000);
        doAppHookStop(1);
        Thread.sleep(2_000);
        assertEquals("Premature anomaly,", 0, getEventMetricDataList().size());

        // Test that alarm does fire when it is supposed to.
        // The anomaly occurs in 1s, but alarms won't fire that quickly.
        // It is likely that the alarm will only fire after this period is already over, but the
        // anomaly should nonetheless be detected when the event stops.
        doAppHookStart(1);
        Thread.sleep(2_000);
        doAppHookStop(1);  // Anomaly should be detected here if the alarm didn't fire yet.
        Thread.sleep(200);
        List<EventMetricData> data = getEventMetricDataList();
        assertEquals("Expected an anomaly,", 1, data.size());
        assertEquals(ALERT_ID, data.get(0).getAtom().getAnomalyDetected().getAlertId());

        turnScreenOff();
    }

    // Tests that anomaly detection for value works.
    public void testValueAnomalyDetection() throws Exception {
        if (!TESTS_ENABLED) return;
        StatsdConfig.Builder config = getBaseConfig(4, // num buckets
                                                    0, // refactory period (seconds)
                                                    6) // threshold: value > 6
                .addValueMetric(ValueMetric.newBuilder()
                        .setId(METRIC_ID)
                        .setWhat(APP_HOOK_MATCH_START_ID)
                        .setBucket(TimeUnit.ONE_MINUTE)
                        // Get the label field's value:
                        .setValueField(FieldMatcher.newBuilder()
                                .setField(Atom.APP_HOOK_FIELD_NUMBER)
                                .addChild(FieldMatcher.newBuilder()
                                        .setField(AppHook.LABEL_FIELD_NUMBER))
                        )

                );
        uploadConfig(config);

        String markTime = getCurrentLogcatDate();
        doAppHookStart(6); // value = 6, which is NOT > trigger
        Thread.sleep(2000);
        if (INCIDENTD_TESTS_ENABLED) assertFalse("Incident", didIncidentdFireSince(markTime));
        assertEquals("Premature anomaly", 0, getEventMetricDataList().size());

        doAppHookStart(14); // value = 14 > trigger
        Thread.sleep(2000);

        if (INCIDENTD_TESTS_ENABLED) assertTrue("No incident", didIncidentdFireSince(markTime));
        List<EventMetricData> data = getEventMetricDataList();
        assertEquals("Expected 1 anomaly", 1, data.size());
        AnomalyDetected a = data.get(0).getAtom().getAnomalyDetected();
        assertEquals("Wrong alert_id", ALERT_ID, a.getAlertId());
    }

    // TODO: Removed until b/73091354 is solved, since it keeps crashing statsd. Update CTS later.
    // Tests that anomaly detection for gauge works.
//    public void testGaugeAnomalyDetection() throws Exception {
//        if (!TESTS_ENABLED) return;
//        StatsdConfig.Builder config = getBaseConfig(1, 20, 6)
//                .addGaugeMetric(GaugeMetric.newBuilder()
//                        .setId(METRIC_ID)
//                        .setWhat(APP_HOOK_MATCH_START_ID)
//                        .setBucket(TimeUnit.ONE_MINUTE)
//                        // Get the label field's value into the gauge:
//                        .setGaugeFieldsFilter(
//                                FieldFilter.newBuilder().setFields(FieldMatcher.newBuilder()
//                                        .setField(Atom.APP_HOOK_FIELD_NUMBER)
//                                        .addChild(FieldMatcher.newBuilder()
//                                                .setField(AppHook.LABEL_FIELD_NUMBER))
//                                )
//                        )
//                );
//        uploadConfig(config);
//
//        String markTime = getCurrentLogcatDate();
//        doAppHookStart(6); // gauge = 6, which is NOT > trigger
//        Thread.sleep(2000);
//        if (INCIDENTD_TESTS_ENABLED) assertFalse("Incident", didIncidentdFireSince(markTime));
//        assertEquals("Premature anomaly", 0, getEventMetricDataList().size());
//
//        doAppHookStart(14); // gauge = 6+1 > trigger
//        Thread.sleep(2000);
//
//        if (INCIDENTD_TESTS_ENABLED) assertTrue("No incident", didIncidentdFireSince(markTime));
//        List<EventMetricData> data = getEventMetricDataList();
//        assertEquals("Expected 1 anomaly", 1, data.size());
//        AnomalyDetected a = data.get(0).getAtom().getAnomalyDetected();
//        assertEquals("Wrong alert_id", ALERT_ID, a.getAlertId());
//    }

    private static final StatsdConfig.Builder getBaseConfig(int numBuckets,
                                                            int refractorySecs,
                                                            long triggerIfSumGt) {
        return StatsdConfig.newBuilder().setId(CONFIG_ID)
                // Items of relevance for detecting the anomaly:
                .addAtomMatcher(StatsdConfigProto.AtomMatcher.newBuilder()
                        .setId(APP_HOOK_MATCH_START_ID)
                        .setSimpleAtomMatcher(StatsdConfigProto.SimpleAtomMatcher.newBuilder()
                                .setAtomId(Atom.APP_HOOK_FIELD_NUMBER)
                                // Event only when the uid is this app's uid.
                                .addFieldValueMatcher(createFvm(AppHook.UID_FIELD_NUMBER)
                                        .setEqInt(APP_HOOK_UID)
                                )
                                .addFieldValueMatcher(createFvm(AppHook.STATE_FIELD_NUMBER)
                                        .setEqInt(AppHook.State.START.ordinal()) // START
                                )
                        )
                )
                .addAtomMatcher(StatsdConfigProto.AtomMatcher.newBuilder()
                        .setId(APP_HOOK_MATCH_STOP_ID)
                        .setSimpleAtomMatcher(StatsdConfigProto.SimpleAtomMatcher.newBuilder()
                                .setAtomId(Atom.APP_HOOK_FIELD_NUMBER)
                                // Event only when the uid is this app's uid.
                                .addFieldValueMatcher(createFvm(AppHook.UID_FIELD_NUMBER)
                                        .setEqInt(APP_HOOK_UID)
                                )
                                .addFieldValueMatcher(createFvm(AppHook.STATE_FIELD_NUMBER)
                                        .setEqInt(AppHook.State.STOP.ordinal()) // STOP
                                )
                        )
                )
                .addAlert(Alert.newBuilder()
                        .setId(ALERT_ID)
                        .setMetricId(METRIC_ID) // The metric itself must yet be added by the test.
                        .setNumBuckets(numBuckets)
                        .setRefractoryPeriodSecs(refractorySecs)
                        .setTriggerIfSumGt(triggerIfSumGt)
                )
                .addSubscription(Subscription.newBuilder()
                        .setId(SUBSCRIPTION_ID_INCIDENTD)
                        .setRuleType(Subscription.RuleType.ALERT)
                        .setRuleId(ALERT_ID)
                        .setIncidentdDetails(IncidentdDetails.newBuilder()
                                .addSection(INCIDENTD_SECTION))
                )
                // We want to trigger anomalies on METRIC_ID, but don't want the actual data.
                .addNoReportMetric(METRIC_ID)
                .addAllowedLogSource("AID_ROOT") // needed for adb cmd AppHook
                .addAllowedLogSource("AID_STATSD") // needed for AnomalyDetected
                // No need in this test for .addAllowedLogSource("AID_SYSTEM")

                // Items of relevance to reporting the anomaly (we do want this data):
                .addAtomMatcher(StatsdConfigProto.AtomMatcher.newBuilder()
                        .setId(ANOMALY_DETECT_MATCH_ID)
                        .setSimpleAtomMatcher(StatsdConfigProto.SimpleAtomMatcher.newBuilder()
                                .setAtomId(Atom.ANOMALY_DETECTED_FIELD_NUMBER)
                                .addFieldValueMatcher(
                                        createFvm(AnomalyDetected.CONFIG_UID_FIELD_NUMBER)
                                                .setEqInt(Integer.parseInt(CONFIG_UID))
                                )
                                .addFieldValueMatcher(
                                        createFvm(AnomalyDetected.CONFIG_ID_FIELD_NUMBER)
                                                .setEqInt(CONFIG_ID)
                                )
                        )
                )
                .addEventMetric(StatsdConfigProto.EventMetric.newBuilder()
                    .setId(ANOMALY_EVENT_ID)
                    .setWhat(ANOMALY_DETECT_MATCH_ID)
                );
    }
}