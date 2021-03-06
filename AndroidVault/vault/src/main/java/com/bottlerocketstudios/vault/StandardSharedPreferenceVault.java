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

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;

import com.bottlerocketstudios.vault.keys.storage.KeyStorage;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import javax.crypto.SecretKey;

/**
 * SecureVault backed by a SharedPreference file.
 */
public class StandardSharedPreferenceVault implements SharedPreferenceVault {
    private static final String TAG = StandardSharedPreferenceVault.class.getSimpleName();

    private static final String STRING_SET_SEPARATOR = "1eRHtJaybutdAsFp2DkfrT1FqMJlLfT7DdgCpQtTaoQWheoeFBZRqt5pgFDH7Cf";

    private static final Pattern FLOAT_REGEX = Pattern.compile("^-?\\d+\\.\\d+$");
    private static final Pattern INTEGER_REGEX = Pattern.compile("^-?\\d+$");
    private static final Pattern BOOLEAN_REGEX = Pattern.compile("^(true|false)$");

    private final Context mContext;
    private String mTransform;
    private KeyStorage mKeyStorage;

    private String mSharedPreferenceName;
    private SharedPreferences mSharedPreferences;
    private List<OnSharedPreferenceChangeListener> mSharedPreferenceChangeListenerList = new LinkedList<>();

    public StandardSharedPreferenceVault(Context context, KeyStorage keyStorage, String prefFileName, String transform) {
        mContext = context.getApplicationContext();
        mKeyStorage = keyStorage;
        mSharedPreferenceName = prefFileName;
        mTransform = transform;
    }

    boolean writeValues(boolean commit, boolean wasCleared, Set<String> removalSet, StronglyTypedBundle stronglyTypedBundle) {
        SharedPreferences.Editor editor = getSharedPreferences().edit();
        Set<String> preferenceKeySet = new HashSet<>();

        if (wasCleared) {
            editor.clear();
        }

        if (removalSet != null) {
            for (String key: removalSet) {
                editor.remove(key);
            }
            preferenceKeySet.addAll(removalSet);
        }

        if (stronglyTypedBundle != null) {
            //Secret key is kept in memory only long enough to use it.
            SecretKey secretKey = mKeyStorage.loadKey(mContext);
            if (secretKey != null) {
                for(String key: stronglyTypedBundle.keySet()) {
                    Class type = stronglyTypedBundle.getTypeForValue(key);
                    if (type == String.class) {
                        writeString(editor, key, secretKey, stronglyTypedBundle.getValue(String.class, key));
                    } else if (type == Long.class) {
                        writeLong(editor, key, secretKey, stronglyTypedBundle.getValue(Long.class, key));
                    } else if (type == Integer.class) {
                        writeInteger(editor, key, secretKey, stronglyTypedBundle.getValue(Integer.class, key));
                    } else if (type == Float.class) {
                        writeFloat(editor, key, secretKey, stronglyTypedBundle.getValue(Float.class, key));
                    } else if (type == Boolean.class) {
                        writeBoolean(editor, key, secretKey, stronglyTypedBundle.getValue(Boolean.class, key));
                    } else if (Set.class.isAssignableFrom(type)) {
                        try {
                            //noinspection unchecked
                            writeStringSet(editor, key, secretKey, stronglyTypedBundle.getValue(Set.class, key));
                        } catch (ClassCastException e) {
                            Log.e(TAG, "Unexpected type of set provided", e);
                            return false;
                        }
                    } else {
                        Log.e(TAG, "Unexpected data type encountered " + type.toString());
                        return false;
                    }
                }
            } else {
                return false;
            }
            preferenceKeySet.addAll(stronglyTypedBundle.keySet());
        } else {
            return false;
        }

        boolean commitSuccess = true;
        if (commit) {
            commitSuccess = editor.commit();
        } else {
            editor.apply();
        }

        if (commitSuccess) {
            notifyListeners(preferenceKeySet);
        }

        return commitSuccess;
    }

    private void writeStringSet(Editor editor, String key, SecretKey secretKey, Set<String> value) {
        StringBuilder stringBuilder = new StringBuilder();
        for (Iterator<String> iterator = value.iterator(); iterator.hasNext();) {
            stringBuilder.append(iterator.next());
            if (iterator.hasNext()) stringBuilder.append(STRING_SET_SEPARATOR);
        }
        writeString(editor, key, secretKey, stringBuilder.toString());
    }

    private void writeBoolean(Editor editor, String key, SecretKey secretKey, Boolean value) {
        writeString(editor, key, secretKey, String.valueOf(value));
    }

    private void writeFloat(Editor editor, String key, SecretKey secretKey, Float value) {
        writeString(editor, key, secretKey, String.valueOf(value));
    }

    private void writeInteger(Editor editor, String key, SecretKey secretKey, Integer value) {
        writeString(editor, key, secretKey, String.valueOf(value));
    }

    private void writeLong(Editor editor, String key, SecretKey secretKey, Long value) {
        writeString(editor, key, secretKey, String.valueOf(value));
    }

    private void writeString(Editor editor, String key, SecretKey secretKey, String value) {
        editor.putString(key, StringEncryptionUtils.encrypt(secretKey, value, CharacterEncodingConstants.UTF_8, mTransform));
    }

    @Override
    public Map<String, ?> getAll() {
        Map<String, Object> resultMap = new HashMap<>();
        SecretKey secretKey = mKeyStorage.loadKey(mContext);
        if (secretKey != null) {
            SharedPreferences sharedPreferences = getSharedPreferences();
            Map<String, ?> sourceMap = sharedPreferences.getAll();
            for (String key : sourceMap.keySet()) {
                String value = getString(key, null, secretKey);
                if (value != null) {
                    if (FLOAT_REGEX.matcher(value).matches()) {
                        resultMap.put(key, Float.valueOf(value));
                    } else if (INTEGER_REGEX.matcher(value).matches()) {
                        Long longValue = Long.valueOf(value);
                        if (longValue <= Integer.MAX_VALUE && longValue >= Integer.MIN_VALUE) {
                            resultMap.put(key, longValue.intValue());
                        } else {
                            resultMap.put(key, longValue);
                        }
                    } else if (BOOLEAN_REGEX.matcher(value).matches()) {
                        resultMap.put(key, Boolean.valueOf(value));
                    } else if (value.contains(STRING_SET_SEPARATOR)) {
                        resultMap.put(key, splitStringSet(value));
                    } else {
                        resultMap.put(key, value);
                    }
                }
            }
        }
        return resultMap;
    }

    @Override
    public String getString(String key, String defaultValue) {
        //Secret key only in memory long enough to read the value.
        return getString(key, defaultValue, mKeyStorage.loadKey(mContext));
    }

    private String getString(String key, String defaultValue, SecretKey secretKey) {
        String result = defaultValue;
        String rawValue = getSharedPreferences().getString(key, null);
        if (rawValue != null && secretKey != null) {
            try {
                result = StringEncryptionUtils.decrypt(secretKey, rawValue, CharacterEncodingConstants.UTF_8, mTransform);
            } catch (StringEncryptionUtils.UnencryptedException e) {
                Log.e(TAG, "Value for key was clear.", e);
            }
        }
        return result;
    }

    @Override
    public Set<String> getStringSet(String key, Set<String> defValues) {
        Set<String> result = defValues;
        String joinedString = getString(key, null);
        if (joinedString != null) {
            result = splitStringSet(joinedString);
        }
        return result;
    }

    private Set<String> splitStringSet(String joinedString) {
        Set<String> result;
        String splits[] = joinedString.split(STRING_SET_SEPARATOR);
        result = new HashSet<>(splits.length);
        result.addAll(Arrays.asList(splits));
        return result;
    }

    @Override
    public int getInt(String key, int defValue) {
        int result = defValue;
        String stringValue = getString(key, null);
        if (stringValue != null) {
            result = Integer.valueOf(stringValue);
        }
        return result;
    }

    @Override
    public long getLong(String key, long defValue) {
        long result = defValue;
        String stringValue = getString(key, null);
        if (stringValue != null) {
            result = Long.valueOf(stringValue);
        }
        return result;
    }

    @Override
    public float getFloat(String key, float defValue) {
        float result = defValue;
        String stringValue = getString(key, null);
        if (stringValue != null) {
            result = Float.valueOf(stringValue);
        }
        return result;
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        boolean result = defValue;
        String stringValue = getString(key, null);
        if (stringValue != null) {
            result = Boolean.valueOf(stringValue);
        }
        return result;
    }

    @Override
    public boolean contains(String key) {
        return getSharedPreferences().contains(key);
    }

    @Override
    public Editor edit() {
        return new StandardSharedPreferenceVaultEditor(this);
    }

    private void notifyListeners(Set<String> preferenceKeySet) {
        for (OnSharedPreferenceChangeListener listener: mSharedPreferenceChangeListenerList) {
            for (String preferenceKey: preferenceKeySet) {
                listener.onSharedPreferenceChanged(this, preferenceKey);
            }
        }
    }

    @Override
    public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
        mSharedPreferenceChangeListenerList.add(listener);
    }

    @Override
    public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
        mSharedPreferenceChangeListenerList.remove(listener);
    }

    @Override
    public void clearStorage() {
        getSharedPreferences().edit().clear().apply();
        mKeyStorage.clearKey(mContext);
    }

    @Override
    public void rekeyStorage(SecretKey secretKey) {
        clearStorage();
        mKeyStorage.saveKey(mContext, secretKey);
    }

    @Override
    public boolean isKeyAvailable() {
        return mKeyStorage.hasKey(mContext);
    }

    private SharedPreferences getSharedPreferences() {
        if (mSharedPreferences == null) {
            if (TextUtils.isEmpty(mSharedPreferenceName)) throw new IllegalStateException("Cannot open preferences before calling setSharedPreferenceFileName");
            mSharedPreferences = mContext.getSharedPreferences(mSharedPreferenceName, Context.MODE_PRIVATE);
        }
        return mSharedPreferences;
    }
}
