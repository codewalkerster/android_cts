/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.autofillservice.cts;

import static android.autofillservice.cts.CannedFillResponse.ResponseType.NULL;
import static android.autofillservice.cts.CannedFillResponse.ResponseType.TIMEOUT;
import static android.autofillservice.cts.Helper.dumpAutofillService;
import static android.autofillservice.cts.Helper.dumpStructure;
import static android.autofillservice.cts.Helper.getActivityName;
import static android.autofillservice.cts.Timeouts.CONNECTION_TIMEOUT;
import static android.autofillservice.cts.Timeouts.FILL_TIMEOUT;
import static android.autofillservice.cts.Timeouts.IDLE_UNBIND_TIMEOUT;
import static android.autofillservice.cts.Timeouts.SAVE_TIMEOUT;

import static com.google.common.truth.Truth.assertWithMessage;

import android.app.assist.AssistStructure;
import android.autofillservice.cts.CannedFillResponse.CannedDataset;
import android.content.ComponentName;
import android.content.IntentSender;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.service.autofill.AutofillService;
import android.service.autofill.Dataset;
import android.service.autofill.FillCallback;
import android.service.autofill.FillContext;
import android.service.autofill.FillResponse;
import android.service.autofill.SaveCallback;
import android.support.annotation.Nullable;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Implementation of {@link AutofillService} used in the tests.
 */
public class InstrumentedAutoFillService extends AutofillService {

    static final String SERVICE_PACKAGE = "android.autofillservice.cts";
    static final String SERVICE_CLASS = "InstrumentedAutoFillService";

    static final String SERVICE_NAME = SERVICE_PACKAGE + "/." + SERVICE_CLASS;

    private static final String TAG = "InstrumentedAutoFillService";

    private static final boolean DUMP_FILL_REQUESTS = false;
    private static final boolean DUMP_SAVE_REQUESTS = false;

    private static final String STATE_CONNECTED = "CONNECTED";
    private static final String STATE_DISCONNECTED = "DISCONNECTED";

    private static final AtomicReference<InstrumentedAutoFillService> sInstance =
            new AtomicReference<>();
    private static final Replier sReplier = new Replier();
    private static final BlockingQueue<String> sConnectionStates = new LinkedBlockingQueue<>();

    private static final Object sLock = new Object();

    // @GuardedBy("sLock")
    private static boolean sIgnoreUnexpectedRequests = false;

    public InstrumentedAutoFillService() {
        sInstance.set(this);
    }

    public static InstrumentedAutoFillService peekInstance() {
        return sInstance.get();
    }

    @Override
    public void onConnected() {
        Log.v(TAG, "onConnected(): " + sConnectionStates);
        sConnectionStates.offer(STATE_CONNECTED);
    }

    @Override
    public void onDisconnected() {
        Log.v(TAG, "onDisconnected(): " + sConnectionStates);
        sConnectionStates.offer(STATE_DISCONNECTED);
    }

    @Override
    public void onFillRequest(android.service.autofill.FillRequest request,
            CancellationSignal cancellationSignal, FillCallback callback) {
        if (DUMP_FILL_REQUESTS) dumpStructure("onFillRequest()", request.getFillContexts());
        synchronized (sLock) {
            if (sIgnoreUnexpectedRequests || !fromSamePackage(request.getFillContexts()))  {
                Log.w(TAG, "Ignoring onFillRequest()");
                return;
            }
        }
        sReplier.onFillRequest(request.getFillContexts(), request.getClientState(),
                cancellationSignal, callback, request.getFlags());
    }

    @Override
    public void onSaveRequest(android.service.autofill.SaveRequest request,
            SaveCallback callback) {
        if (DUMP_SAVE_REQUESTS) dumpStructure("onSaveRequest()", request.getFillContexts());
        synchronized (sLock) {
            if (sIgnoreUnexpectedRequests || !fromSamePackage(request.getFillContexts())) {
                Log.w(TAG, "Ignoring onSaveRequest()");
                return;
            }
        }
        sReplier.onSaveRequest(request.getFillContexts(), request.getClientState(), callback,
                request.getDatasetIds());
    }

    private boolean fromSamePackage(List<FillContext> contexts) {
        final ComponentName component = contexts.get(contexts.size() - 1).getStructure()
                .getActivityComponent();
        final String actualPackage = component.getPackageName();
        if (!actualPackage.equals(getPackageName())
                && !actualPackage.equals(sReplier.mAcceptedPackageName)) {
            Log.w(TAG, "Got request from package " + actualPackage);
            return false;
        }
        return true;
    }

    /**
     * Sets whether unexpected calls to
     * {@link #onFillRequest(android.service.autofill.FillRequest, CancellationSignal, FillCallback)}
     * should throw an exception.
     */
    public static void setIgnoreUnexpectedRequests(boolean ignore) {
        synchronized (sLock) {
            sIgnoreUnexpectedRequests = ignore;
        }
    }

    /**
     * Waits until {@link #onConnected()} is called, or fails if it times out.
     *
     * <p>This method is useful on tests that explicitly verifies the connection, but should be
     * avoided in other tests, as it adds extra time to the test execution - if a text needs to
     * block until the service receives a callback, it should use
     * {@link Replier#getNextFillRequest()} instead.
     */
    static void waitUntilConnected() throws Exception {
        final String state = CONNECTION_TIMEOUT.run("waitUntilConnected()", () -> {
            final String polled =
                    sConnectionStates.poll(CONNECTION_TIMEOUT.ms(), TimeUnit.MILLISECONDS);
            if (polled == null) {
                dumpAutofillService();
            }
            return polled;
        });
        assertWithMessage("Invalid connection state").that(state).isEqualTo(STATE_CONNECTED);
    }

    /**
     * Waits until {@link #onDisconnected()} is called, or fails if it times out.
     *
     * <p>This method is useful on tests that explicitly verifies the connection, but should be
     * avoided in other tests, as it adds extra time to the test execution.
     */
    static void waitUntilDisconnected() throws Exception {
        final String state = IDLE_UNBIND_TIMEOUT.run("waitUntilDisconnected()", () -> {
            return sConnectionStates.poll(2 * IDLE_UNBIND_TIMEOUT.ms(), TimeUnit.MILLISECONDS);
        });
        assertWithMessage("Invalid connection state").that(state).isEqualTo(STATE_DISCONNECTED);
    }

    /**
     * Gets the {@link Replier} singleton.
     */
    static Replier getReplier() {
        return sReplier;
    }

    static void resetStaticState() {
        sConnectionStates.clear();
    }

    /**
     * POJO representation of the contents of a
     * {@link AutofillService#onFillRequest(android.service.autofill.FillRequest,
     * CancellationSignal, FillCallback)} that can be asserted at the end of a test case.
     */
    static final class FillRequest {
        final AssistStructure structure;
        final List<FillContext> contexts;
        final Bundle data;
        final CancellationSignal cancellationSignal;
        final FillCallback callback;
        final int flags;

        private FillRequest(List<FillContext> contexts, Bundle data,
                CancellationSignal cancellationSignal, FillCallback callback, int flags) {
            this.contexts = contexts;
            this.data = data;
            this.cancellationSignal = cancellationSignal;
            this.callback = callback;
            this.flags = flags;
            structure = contexts.get(contexts.size() - 1).getStructure();
        }
    }

    /**
     * POJO representation of the contents of a
     * {@link AutofillService#onSaveRequest(android.service.autofill.SaveRequest, SaveCallback)}
     * that can be asserted at the end of a test case.
     */
    static final class SaveRequest {
        public final List<FillContext> contexts;
        public final AssistStructure structure;
        public final Bundle data;
        public final SaveCallback callback;
        public final List<String> datasetIds;

        private SaveRequest(List<FillContext> contexts, Bundle data, SaveCallback callback,
                List<String> datasetIds) {
            if (contexts != null && contexts.size() > 0) {
                structure = contexts.get(contexts.size() - 1).getStructure();
            } else {
                structure = null;
            }
            this.contexts = contexts;
            this.data = data;
            this.callback = callback;
            this.datasetIds = datasetIds;
        }
    }

    /**
     * Object used to answer a
     * {@link AutofillService#onFillRequest(android.service.autofill.FillRequest,
     * CancellationSignal, FillCallback)}
     * on behalf of a unit test method.
     */
    static final class Replier {

        private final BlockingQueue<CannedFillResponse> mResponses = new LinkedBlockingQueue<>();
        private final BlockingQueue<FillRequest> mFillRequests = new LinkedBlockingQueue<>();
        private final BlockingQueue<SaveRequest> mSaveRequests = new LinkedBlockingQueue<>();

        private List<Throwable> mExceptions;
        private IntentSender mOnSaveIntentSender;
        private String mAcceptedPackageName;

        private Replier() {
        }

        private IdMode mIdMode = IdMode.RESOURCE_ID;

        public void setIdMode(IdMode mode) {
            this.mIdMode = mode;
        }

        public void acceptRequestsFromPackage(String packageName) {
            mAcceptedPackageName = packageName;
        }

        /**
         * Gets the exceptions thrown asynchronously, if any.
         */
        @Nullable List<Throwable> getExceptions() {
            return mExceptions;
        }

        private void addException(@Nullable Throwable e) {
            if (e == null) return;

            if (mExceptions == null) {
                mExceptions = new ArrayList<>();
            }
            mExceptions.add(e);
        }

        /**
         * Sets the expectation for the next {@code onFillRequest} as {@link FillResponse} with just
         * one {@link Dataset}.
         */
        Replier addResponse(CannedDataset dataset) {
            return addResponse(new CannedFillResponse.Builder()
                    .addDataset(dataset)
                    .build());
        }

        /**
         * Sets the expectation for the next {@code onFillRequest}.
         */
        Replier addResponse(CannedFillResponse response) {
            if (response == null) {
                throw new IllegalArgumentException("Cannot be null - use NO_RESPONSE instead");
            }
            mResponses.add(response);
            return this;
        }

        /**
         * Sets the {@link IntentSender} that is passed to
         * {@link SaveCallback#onSuccess(IntentSender)}.
         */
        void setOnSave(IntentSender intentSender) {
            mOnSaveIntentSender = intentSender;
        }

        /**
         * Gets the next fill request, in the order received.
         *
         * <p>Typically called at the end of a test case, to assert the initial request.
         */
        FillRequest getNextFillRequest() throws InterruptedException {
            final FillRequest request =
                    mFillRequests.poll(FILL_TIMEOUT.ms(), TimeUnit.MILLISECONDS);
            if (request == null) {
                throw new RetryableException(FILL_TIMEOUT, "onFillRequest() not called");
            }
            return request;
        }

        /**
         * Asserts the total number of {@link AutofillService#onFillRequest(
         * android.service.autofill.FillRequest,  CancellationSignal, FillCallback)}, minus those
         * returned by {@link #getNextFillRequest()}.
         */
        void assertNumberUnhandledFillRequests(int expected) {
            assertWithMessage("Invalid number of fill requests").that(mFillRequests.size())
                    .isEqualTo(expected);
        }

        /**
         * Gets the current number of unhandled requests.
         */
        int getNumberUnhandledFillRequests() {
            return mFillRequests.size();
        }

        /**
         * Gets the next save request, in the order received.
         *
         * <p>Typically called at the end of a test case, to assert the initial request.
         */
        SaveRequest getNextSaveRequest() throws InterruptedException {
            final SaveRequest request =
                    mSaveRequests.poll(SAVE_TIMEOUT.ms(), TimeUnit.MILLISECONDS);
            if (request == null) {
                throw new RetryableException(SAVE_TIMEOUT, "onSaveRequest() not called");
            }
            return request;
        }

        /**
         * Asserts the total number of
         * {@link AutofillService#onSaveRequest(android.service.autofill.SaveRequest, SaveCallback)}
         * minus those returned by {@link #getNextSaveRequest()}.
         */
        void assertNumberUnhandledSaveRequests(int expected) {
            assertWithMessage("Invalid number of save requests").that(mSaveRequests.size())
                    .isEqualTo(expected);
        }

        /**
         * Resets its internal state.
         */
        void reset() {
            mResponses.clear();
            mFillRequests.clear();
            mSaveRequests.clear();
            mExceptions = null;
            mOnSaveIntentSender = null;
            mAcceptedPackageName = null;
        }

        private void onFillRequest(List<FillContext> contexts, Bundle data,
                CancellationSignal cancellationSignal, FillCallback callback, int flags) {
            try {
                CannedFillResponse response = null;
                try {
                    response = mResponses.poll(CONNECTION_TIMEOUT.ms(), TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Log.w(TAG, "Interrupted getting CannedResponse: " + e);
                    Thread.currentThread().interrupt();
                    addException(e);
                    return;
                }
                if (response == null) {
                    final String msg = "onFillRequest() for activity " + getActivityName(contexts)
                            + " received when no canned response was set.";
                    dumpStructure(msg, contexts);
                    addException(new RetryableException(msg));
                    return;
                }
                if (response.getResponseType() == NULL) {
                    Log.d(TAG, "onFillRequest(): replying with null");
                    callback.onSuccess(null);
                    return;
                }

                if (response.getResponseType() == TIMEOUT) {
                    Log.d(TAG, "onFillRequest(): not replying at all");
                    return;
                }

                final String failureMessage = response.getFailureMessage();
                if (failureMessage != null) {
                    Log.v(TAG, "onFillRequest(): failureMessage = " + failureMessage);
                    callback.onFailure(failureMessage);
                    return;
                }

                final FillResponse fillResponse;

                switch (mIdMode) {
                    case RESOURCE_ID:
                        fillResponse = response.asFillResponse(
                                (id) -> Helper.findNodeByResourceId(contexts, id));
                        break;
                    case HTML_NAME:
                        fillResponse = response.asFillResponse(
                                (name) -> Helper.findNodeByHtmlName(contexts, name));
                        break;
                    case HTML_NAME_OR_RESOURCE_ID:
                        fillResponse = response.asFillResponse(
                                (id) -> Helper.findNodeByHtmlNameOrResourceId(contexts, id));
                        break;
                    default:
                        throw new IllegalStateException("Unknown id mode: " + mIdMode);
                }

                Log.v(TAG, "onFillRequest(): fillResponse = " + fillResponse);
                callback.onSuccess(fillResponse);
            } catch (Exception e) {
                addException(e);
            } finally {
                mFillRequests.offer(new FillRequest(contexts, data, cancellationSignal, callback,
                        flags));
            }
        }

        private void onSaveRequest(List<FillContext> contexts, Bundle data, SaveCallback callback,
                List<String> datasetIds) {
            Log.d(TAG, "onSaveRequest(): sender=" + mOnSaveIntentSender);
            mSaveRequests.offer(new SaveRequest(contexts, data, callback, datasetIds));
            if (mOnSaveIntentSender != null) {
                callback.onSuccess(mOnSaveIntentSender);
            } else {
                callback.onSuccess();
            }
        }
    }
}
