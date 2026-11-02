package com.toji.settings;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.VideoView;

/** Tela inicial: reproduz o MP4 original em loop até o usuário tocar em ENTRAR. */
public class IntroActivity extends Activity {
    private VideoView video;
    private boolean entering = false;
    private int dp(int n) { return (int) (n * getResources().getDisplayMetrics().density + 0.5f); }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        video = new VideoView(this);
        // CENTER_CROP-like effect using a VideoView: preserve entire frame instead of cropping content.
        root.addView(video, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        video.setVideoURI(Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.toji_intro_original));
        video.setOnPreparedListener(mp -> {
            mp.setLooping(true);
            mp.setVolume(0f, 0f); // áudio desligado para não surpreender na abertura
            video.start();
        });
        video.setOnErrorListener((mp, what, extra) -> {
            // O botão ENTRAR continua funcionando mesmo em dispositivos sem suporte ao codec.
            return true;
        });
        TextView enter = new TextView(this);
        enter.setText("ENTRAR");
        enter.setTextSize(18);
        enter.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        enter.setTextColor(Color.WHITE);
        enter.setGravity(Gravity.CENTER);
        enter.setPadding(dp(30), dp(15), dp(30), dp(15));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xff8435c9);
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(2), 0xffc98aff);
        enter.setBackground(bg);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, dp(60), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.setMargins(dp(32), 0, dp(32), dp(54));
        root.addView(enter, lp);
        enter.setElevation(dp(12));
        enter.setOnClickListener(v -> {
            if (entering) return;
            entering = true;
            if (video != null) video.stopPlayback();
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
        setContentView(root);
    }
    @Override protected void onResume() {
        super.onResume();
        if (video != null && !video.isPlaying() && !entering) video.start();
    }
    @Override protected void onPause() {
        if (video != null && video.isPlaying()) video.pause();
        super.onPause();
    }
    @Override protected void onDestroy() {
        if (video != null) video.stopPlayback();
        super.onDestroy();
    }
}
