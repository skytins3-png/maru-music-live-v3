package com.maru.musiclive;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.net.Uri;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.io.InputStream;

/**
 * A single edge-to-edge image OR muted looping video for Samsung pop-up mode.
 * No album list, file names, video audio, service, background capture, or BIGO injection.
 * No SYSTEM_ALERT_WINDOW permission: this is the host Activity's own view.
 */
public final class SingleMediaPreviewView extends FrameLayout
        implements TextureView.SurfaceTextureListener {

    private final ImageView picture;
    private final TextureView video;
    private MediaPlayer player;
    private Surface surface;
    private Uri currentVideo;
    private int playerGeneration;
    private boolean released;

    public SingleMediaPreviewView(Context context) {
        super(context);
        setBackgroundColor(Color.BLACK);
        setClipToPadding(true);
        setClipChildren(true);

        video = new TextureView(context);
        video.setSurfaceTextureListener(this);
        video.setVisibility(View.GONE);
        addView(video, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        picture = new ImageView(context);
        picture.setScaleType(ImageView.ScaleType.CENTER_CROP);
        picture.setImageResource(R.drawable.actual_image_01);
        addView(picture, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    public void show(Uri uri, String mime) {
        if (released) return;
        releasePlayer();
        if (uri == null || !SingleMediaPreviewPolicy.accept(mime, uri.toString())) {
            showDefault();
            return;
        }
        if (SingleMediaPreviewPolicy.isVideo(mime, uri.toString())) {
            currentVideo = uri;
            picture.setVisibility(View.VISIBLE); // avoid blank frame while video prepares
            video.setVisibility(View.VISIBLE);
            if (video.isAvailable()) openVideo(video.getSurfaceTexture());
            return;
        }
        currentVideo = null;
        video.setVisibility(View.GONE);
        Bitmap bmp = loadSampledBitmap(uri);
        if (bmp == null) {
            showDefault();
            return;
        }
        picture.setImageBitmap(bmp);
        picture.setVisibility(View.VISIBLE);
    }

    private Bitmap loadSampledBitmap(Uri uri) {
        try {
            BitmapFactory.Options probe = new BitmapFactory.Options();
            probe.inJustDecodeBounds = true;
            try (InputStream in = getContext().getContentResolver().openInputStream(uri)) {
                if (in == null) return null;
                BitmapFactory.decodeStream(in, null, probe);
            }
            if (probe.outWidth < 1 || probe.outHeight < 1) return null;
            int sample = 1;
            while (probe.outWidth / sample > 1600
                    || probe.outHeight / sample > 2400) sample *= 2;
            BitmapFactory.Options scaled = new BitmapFactory.Options();
            scaled.inSampleSize = sample;
            try (InputStream in = getContext().getContentResolver().openInputStream(uri)) {
                return in == null ? null : BitmapFactory.decodeStream(in, null, scaled);
            }
        } catch (RuntimeException | java.io.IOException ex) {
            return null;
        }
    }

    private void showDefault() {
        currentVideo = null;
        video.setVisibility(View.GONE);
        picture.setImageResource(R.drawable.actual_image_01);
        picture.setVisibility(View.VISIBLE);
    }

    private void openVideo(SurfaceTexture texture) {
        if (released || currentVideo == null || texture == null) return;
        releasePlayer();
        final int generation = ++playerGeneration;
        final MediaPlayer next = new MediaPlayer();
        player = next;
        try {
            surface = new Surface(texture);
            next.setSurface(surface);
            next.setDataSource(getContext(), currentVideo);
            next.setLooping(true);
            next.setVolume(0f, 0f); // keep the actual MARU song/host audio separate
            next.setOnPreparedListener(mp -> {
                if (released || generation != playerGeneration || mp != player) return;
                transformToCenterCrop(mp.getVideoWidth(), mp.getVideoHeight());
                mp.start();
            });
            next.setOnVideoSizeChangedListener((mp,w,h) -> {
                if (generation == playerGeneration) transformToCenterCrop(w,h);
            });
            next.setOnInfoListener((mp, what, extra) -> {
                if (generation == playerGeneration
                        && what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                    picture.setVisibility(View.GONE);
                }
                return false;
            });
            next.setOnErrorListener((mp, what, extra) -> {
                if (generation == playerGeneration && !released) {
                    releasePlayer();
                    showDefault(); // never leave a black failed video
                }
                return true;
            });
            next.prepareAsync();
        } catch (Exception error) {
            releasePlayer();
            showDefault();
        }
    }

    private void transformToCenterCrop(int width, int height) {
        if (width <= 0 || height <= 0) return;
        int viewW = video.getWidth(), viewH = video.getHeight();
        if (viewW <= 0 || viewH <= 0) {
            video.post(() -> transformToCenterCrop(width, height));
            return;
        }
        float aspectVideo = (float)width / height;
        float aspectView = (float)viewW / viewH;
        float scaleX = aspectView > aspectVideo ? 1f : aspectVideo / aspectView;
        float scaleY = aspectView > aspectVideo ? aspectView / aspectVideo : 1f;
        Matrix matrix = new Matrix();
        matrix.setScale(scaleX, scaleY, viewW / 2f, viewH / 2f);
        video.setTransform(matrix);
    }

    public void stopForLeavingScreen() {
        released = true;
        releasePlayer();
        currentVideo = null;
    }

    private void releasePlayer() {
        ++playerGeneration;
        if (player != null) {
            player.setOnErrorListener(null);
            player.setOnInfoListener(null);
            player.setOnPreparedListener(null);
            player.setOnVideoSizeChangedListener(null);
            try { player.reset(); } catch (RuntimeException ignored) {}
            player.release();
            player = null;
        }
        if (surface != null) {
            surface.release();
            surface = null;
        }
    }

    @Override public void onSurfaceTextureAvailable(SurfaceTexture t, int w, int h) {
        if (currentVideo != null && !released) openVideo(t);
    }
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture t, int w, int h) {
        if (player != null) transformToCenterCrop(player.getVideoWidth(), player.getVideoHeight());
    }
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture t) {
        releasePlayer();
        return true;
    }
    @Override public void onSurfaceTextureUpdated(SurfaceTexture t) {}
    @Override protected void onDetachedFromWindow() {
        releasePlayer();
        super.onDetachedFromWindow();
    }
}
