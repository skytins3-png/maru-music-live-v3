package com.maru.radio;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
/** Compatibility entry point: all songs now belong to one library. */
public final class RadioArtistsActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        startActivity(new Intent(this, RadioActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
        finish();
    }
}
