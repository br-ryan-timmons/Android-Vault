/*
 * Copyright (c) 2016. Bottle Rocket LLC
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bottlerocketstudios.vault;

import android.content.SharedPreferences;

import javax.crypto.SecretKey;

/**
 * Shared Preferences backed vault for storing sensitive information.
 */
public interface SharedPreferenceVault extends SharedPreferences {

    /**
     * Read the value corresponding to the provided key or return defaultValue on failure.
     */
    String getString(String key, String defaultValue);

    /**
     * Remove all stored values and destroy cryptographic keys associated with the vault instance.
     * <strong>This will permanently destroy all data in the preference file.</strong>
     */
    void clearStorage();

    /**
     * Remove all stored values and destroy cryptographic keys associated with the vault instance.
     * Configure the vault to use the newly provided key for future data.
     * <strong>This will permanently destroy all data in the preference file.</strong>
     */
    void rekeyStorage(SecretKey secretKey);

    /**
     * Determine if this instance of storage currently has a valid key with which to encrypt values.
     */
    boolean isKeyAvailable();
}
