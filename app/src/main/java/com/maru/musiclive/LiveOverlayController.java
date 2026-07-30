package com.maru.musiclive;

import android.content.Context;
import android.content.Intent;

public final class LiveOverlayController {
    public static final String ACTION_SHOW = "com.maru.musiclive.SHOW_EVENT_OVERLAY";
    public static final String EXTRA_TYPE = "type";
    public static final String EXTRA_NAME = "name";
    public static final String EXTRA_DETAIL = "detail";
    public static final String EXTRA_LANGUAGE = "language";
    public static final String EXTRA_TEXT = "text";
    public static final String EXTRA_DURATION = "duration";
    public static final String EXTRA_PRIORITY = "priority";

    private LiveOverlayController() {}

    public static void show(Context context, LiveEvent event) {
        if (context == null || event == null) return;
        Intent intent = new Intent(ACTION_SHOW)
                .setPackage(context.getPackageName())
                .putExtra(EXTRA_TYPE, event.type.name())
                .putExtra(EXTRA_NAME, event.nickname)
                .putExtra(EXTRA_DETAIL, event.detail)
                .putExtra(EXTRA_LANGUAGE, event.languageHint)
                .putExtra(EXTRA_TEXT, overlayText(event))
                .putExtra(EXTRA_DURATION, event.type.overlayDurationMs)
                .putExtra(EXTRA_PRIORITY, event.type.priority);
        context.sendBroadcast(intent);
    }

    public static void showDialogue(
            Context context,
            String nickname,
            String message,
            String language) {
        show(context, new LiveEvent(
                EventType.CHAT,
                nickname,
                message,
                message,
                language,
                System.currentTimeMillis()));
    }

    public static String overlayText(LiveEvent event) {
        return EventOverlayText.format(event);
    }
}
