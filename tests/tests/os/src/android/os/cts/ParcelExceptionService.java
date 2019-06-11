/*
 * Copyright (C) 2019 The Android Open Source Project
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
 * limitations under the License
 */

package android.os.cts;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;

public class ParcelExceptionService extends Service {
    @Override
    public IBinder onBind(Intent intent) {
        return new ParcelExceptionServiceImpl();
    }

    private static class ParcelExceptionServiceImpl extends IParcelExceptionService.Stub {
        private final IBinder mBinder = new Binder();


        @Override
        public ExceptionalParcelable writeBinderThrowException() throws RemoteException {
            return new ExceptionalParcelable(mBinder);
        }
    }
}
