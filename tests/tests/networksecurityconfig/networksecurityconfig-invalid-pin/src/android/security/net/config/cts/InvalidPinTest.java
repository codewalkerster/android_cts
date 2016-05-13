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

package android.security.net.config.cts;

import junit.framework.TestCase;

public class InvalidPinTest extends TestCase {

    public void testPinFailure() throws Exception {
        TestUtils.assertTlsConnectionFails("android.com", 443);
    }

    public void testDefaultDomainUnaffected() throws Exception {
        TestUtils.assertTlsConnectionSucceeds("example.com", 443);
        TestUtils.assertTlsConnectionSucceeds("developer.android.com", 443);
    }
}
