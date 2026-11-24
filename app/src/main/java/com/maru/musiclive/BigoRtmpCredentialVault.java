package com.maru.musiclive;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.GeneralSecurityException;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Stream key never resides in the repository, or ordinary plaintext SharedPreferences.
 * Stored ciphertext is only decryptable on this Android installation.
 */
public final class BigoRtmpCredentialVault {
    private static final String ALIAS="maru_bigo_rtmp_20261124_aes";
    private static final String NAME="maru_secure_rtmp";
    private BigoRtmpCredentialVault() {}

    public static void save(Context context,String server,String key)
            throws GeneralSecurityException {
        if (!BigoRtmpEndpointPolicy.validServer(server)
                || !BigoRtmpEndpointPolicy.validStreamKey(key))
            throw new GeneralSecurityException("Invalid endpoint");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,ensureKey());
        byte[] data=cipher.doFinal(key.getBytes(StandardCharsets.UTF_8));
        SharedPreferences prefs=context.getSharedPreferences(NAME,Context.MODE_PRIVATE);
        prefs.edit().putString("server",server)
                .putString("enc",Base64.encodeToString(data,Base64.NO_WRAP))
                .putString("iv",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP))
                .apply();
    }

    public static String server(Context context) {
        return context.getSharedPreferences(NAME,Context.MODE_PRIVATE)
                .getString("server","");
    }

    public static String loadKey(Context context) throws GeneralSecurityException {
        SharedPreferences prefs=context.getSharedPreferences(NAME,Context.MODE_PRIVATE);
        String enc=prefs.getString("enc","");
        String iv=prefs.getString("iv","");
        if (enc.isEmpty() || iv.isEmpty()) return "";
        byte[] ivBytes=Base64.decode(iv,Base64.NO_WRAP);
        if (ivBytes.length!=12)throw new GeneralSecurityException("Bad key IV");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,ensureKey(),new GCMParameterSpec(128,ivBytes));
        return new String(cipher.doFinal(Base64.decode(enc,Base64.NO_WRAP)),
                StandardCharsets.UTF_8);
    }

    public static void clear(Context context) {
        context.getSharedPreferences(NAME,Context.MODE_PRIVATE)
                .edit().clear().apply();
    }

    private static SecretKey ensureKey() throws GeneralSecurityException {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");
        try { ks.load(null); }
        catch (java.io.IOException ex) { throw new GeneralSecurityException(ex); }
        SecretKey old=(SecretKey)ks.getKey(ALIAS,null);
        if (old!=null)return old;
        KeyGenerator gen=KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        gen.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build());
        return gen.generateKey();
    }
}
