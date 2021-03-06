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

package com.bottlerocketstudios.vault.keys.generator;

import com.bottlerocketstudios.vault.EncryptionConstants;
import com.bottlerocketstudios.vault.salt.PrngSaltGenerator;

import javax.crypto.SecretKey;

/**
 * Create an AES256 key from a user supplied password.
 */
public class Aes256KeyFromPasswordFactory {

    public static final int SALT_SIZE_BYTES = 512;

    /**
     * This will execute the key generation for the number of supplied iterations. This will block for
     * a while depending on processor speed.
     */
    public static SecretKey createKey(String password, int pbkdfIterations) {
        PbkdfKeyGenerator keyGenerator = new PbkdfKeyGenerator(pbkdfIterations, EncryptionConstants.AES_256_KEY_LENGTH_BITS, new PrngSaltGenerator(), SALT_SIZE_BYTES);
        return keyGenerator.generateKey(password);
    }

}
