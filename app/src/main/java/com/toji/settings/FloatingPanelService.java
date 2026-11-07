
package com.toji.settings;

import android.app.Service;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

public class FloatingPanelService extends Service {
    private static final int PURPLE = 0xFF7545ED;
    private static final int DARK = 0xFF111827;
    private static final int FIELD = 0xFF273244;
    private static final int WHITE = Color.WHITE;
    private static final int TEST_MS = 15000;

    private WindowManager wm;
    private WindowManager.LayoutParams params;
    private LinearLayout root, header, content;
    private ScrollView panel;
    private TextView title, status, sliderLabel;
    private EditText widthField, heightField;
    private final Handler main = new Handler(Looper.getMainLooper());
    private Runnable timer;
    private boolean expanded, testing, busy, destroyed;
    private int bubbleX, bubbleY;

    private int dp(int n) {
        return Math.round(n * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private TextView text(String value, int size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    private void button(String value, int color, Runnable action) {
        TextView b = text(value, 13, WHITE);
        b.setGravity(Gravity.CENTER);
        b.setBackground(shape(color, 11));
        b.setPadding(dp(7), dp(11), dp(7), dp(11));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(7);
        content.addView(b, lp);
        b.setOnClickListener(v -> action.run());
    }

    private EditText number(String value) {
        EditText e = new EditText(this);
        e.setText(value);
        e.setSingleLine(true);
        e.setTextColor(WHITE);
        e.setTextSize(15);
        e.setGravity(Gravity.CENTER);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.setBackground(shape(FIELD, 10));
        e.setSelectAllOnFocus(true);
        e.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                setFocusable(true);
            }
            return false;
        });
        return e;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        params = new WindowManager.LayoutParams(
                -2, -2, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = dp(10);
        params.y = dp(115);
        bubbleX = params.x;
        bubbleY = params.y;
        build();
        try {
            wm.addView(root, params);
        } catch (Exception e) {
            stopSelf();
        }
    }

    private void build() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(3), dp(3), dp(9), dp(3));
        header.setBackground(shape(DARK, 22));

        TextView bubble = text("T", 14, WHITE);
        bubble.setGravity(Gravity.CENTER);
        bubble.setTypeface(null, 1);
        bubble.setBackground(shape(PURPLE, 30));
        header.addView(bubble, new LinearLayout.LayoutParams(dp(34), dp(34)));

        // Puxador roxo no tema do Toji.
        TextView grip = text("  ━━", 14, PURPLE);
        header.addView(grip);
        title = text("  TOJI | STRETCH", 12, WHITE);
        title.setTypeface(null, 1);
        title.setVisibility(View.GONE);
        header.addView(title);
        root.addView(header);
        drag(header);

        panel = new ScrollView(this);
        panel.setFillViewport(false);
        panel.setVerticalScrollBarEnabled(true);
        panel.setBackground(shape(DARK, 15));
        panel.setVisibility(View.GONE);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(12), dp(12), dp(10));
        panel.addView(content);
        root.addView(panel);

        TextView heading = text("RESOLUÇÃO", 16, WHITE);
        heading.setTypeface(null, 1);
        heading.setPadding(0, 0, 0, dp(10));
        content.addView(heading);

        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.HORIZONTAL);
        widthField = number("1080");
        heightField = number("1920");
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(0, dp(44), 1);
        wlp.rightMargin = dp(6);
        fields.addView(widthField, wlp);
        fields.addView(heightField, new LinearLayout.LayoutParams(0, dp(44), 1));
        content.addView(fields);

        TextView hint = text("LARGURA                          ALTURA", 10, 0xFFADB5C6);
        hint.setPadding(0, dp(3), 0, dp(10));
        content.addView(hint);

        sliderLabel = text("ALTURA • 1920", 12, 0xFFB9A1FF);
        content.addView(sliderLabel);
        SeekBar slider = new SeekBar(this);
        slider.setMax(80);
        slider.setProgress(16);
        slider.setProgressTintList(ColorStateList.valueOf(PURPLE));
        slider.setThumbTintList(ColorStateList.valueOf(PURPLE));
        content.addView(slider);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                int h = 1600 + progress * 20;
                sliderLabel.setText("ALTURA • " + h);
                if (fromUser) heightField.setText(String.valueOf(h));
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });

        button("TESTAR RESOLUÇÃO", PURPLE, () -> {
            try {
                int w = Integer.parseInt(widthField.getText().toString().trim());
                int h = Integer.parseInt(heightField.getText().toString().trim());
                apply(w, h);
            } catch (NumberFormatException ex) {
                message("Digite largura e altura válidas.");
            }
        });

        button("MANTER RESOLUÇÃO", FIELD, () -> {
            if (busy || !testing) {
                message("Nenhum teste pendente.");
                return;
            }
            testing = false;
            cancelTimer();
            message("Resolução mantida. Restaure quando quiser.");
        });

        button("RESTAURAR TELA", FIELD, () -> restore(false));
        button("ABRIR BLOOD STRIKE", FIELD, this::openBlood);
        button("MINIMIZAR PARA BOLINHA", FIELD, () -> expand(false));
        button("FECHAR TUDO", 0xFF433050, this::closeAll);
        status = text("Pronto para testar.", 11, 0xFFB9A1FF);
        status.setPadding(0, dp(6), 0, 0);
        content.addView(status);
    }

    private void drag(View handle) {
        final int slop = ViewConfiguration.get(this).getScaledTouchSlop();
        handle.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY;
            int startX, startY;
            boolean moved;

            @Override public boolean onTouch(View view, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = e.getRawX();
                        downY = e.getRawY();
                        startX = params.x;
                        startY = params.y;
                        moved = false;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        int dx = Math.round(e.getRawX() - downX);
                        int dy = Math.round(e.getRawY() - downY);
                        if (Math.abs(dx) > slop || Math.abs(dy) > slop) moved = true;
                        if (moved) {
                            params.x = Math.max(0, startX + dx);
                            params.y = Math.max(0, startY + dy);
                            if (!expanded) {
                                bubbleX = params.x;
                                bubbleY = params.y;
                            }
                            updateWindow();
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        if (!moved) expand(!expanded);
                        return true;

                    case MotionEvent.ACTION_CANCEL:
                        return true;
                }
                return false;
            }
        });
    }

    private void setFocusable(boolean enabled) {
        if (params == null) return;
        if (enabled) {
            params.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        } else {
            params.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        }
        params.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        updateWindow();
    }

    private void hideKeyboard() {
        if (root == null) return;
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(root.getWindowToken(), 0);
        root.clearFocus();
        setFocusable(false);
    }

    private void expand(boolean show) {
        if (root == null || panel == null) return;
        hideKeyboard();
        if (show && !expanded) {
            bubbleX = params.x;
            bubbleY = params.y;
        }
        expanded = show;
        title.setVisibility(show ? View.VISIBLE : View.GONE);
        panel.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            int screenW = getResources().getDisplayMetrics().widthPixels;
            int screenH = getResources().getDisplayMetrics().heightPixels;
            int width = Math.max(dp(170), Math.min(dp(285), screenW - dp(20)));
            int height = Math.max(dp(95), Math.min(dp(340), screenH - dp(85)));
            panel.setLayoutParams(new LinearLayout.LayoutParams(width, height));
            header.setLayoutParams(new LinearLayout.LayoutParams(width, -2));
            params.x = Math.max(0, Math.min(params.x, screenW - width - dp(4)));
            params.y = dp(5);
        } else {
            header.setLayoutParams(new LinearLayout.LayoutParams(-2, -2));
            params.x = bubbleX;
            params.y = bubbleY;
        }
        updateWindow();
    }

    private void updateWindow() {
        if (wm == null || root == null || root.getWindowToken() == null) return;
        try { wm.updateViewLayout(root, params); } catch (Exception ignored) { }
    }

    private void message(String value) {
        if (status != null) status.setText(value);
    }

    private void cancelTimer() {
        if (timer != null) main.removeCallbacks(timer);
        timer = null;
    }

    private void apply(int width, int height) {
        if (busy || testing) {
            message("Confirme ou restaure o teste anterior.");
            return;
        }
        if (width < 600 || width > 3000 || height < 600 || height > 4000) {
            message("Resolução fora dos limites.");
            return;
        }
        hideKeyboard();
        busy = true;
        message("Aplicando via Shizuku...");
        new Thread(() -> {
            ResolutionController.Result result = ResolutionController.apply(
                    getApplicationContext(), width, height);
            if (destroyed) {
                if (result.ok) ResolutionController.restore(getApplicationContext());
                return;
            }
            main.post(() -> {
                if (destroyed) return;
                busy = false;
                if (!result.ok) {
                    message(result.message);
                    return;
                }
                testing = true;
                message("Teste ativo por 15 segundos. Toque MANTER para confirmar.");
                cancelTimer();
                timer = () -> { if (testing && !busy) restore(false); };
                main.postDelayed(timer, TEST_MS);
            });
        }, "Toji-Apply").start();
    }

    private void restore(boolean closeAfter) {
        if (busy) {
            message("Aguarde o comando terminar.");
            return;
        }
        busy = true;
        testing = false;
        cancelTimer();
        hideKeyboard();
        message("Restaurando resolução...");
        new Thread(() -> {
            ResolutionController.Result result = ResolutionController.restore(getApplicationContext());
            main.post(() -> {
                if (destroyed) return;
                busy = false;
                message(result.message);
                if (closeAfter && result.ok) stopSelf();
            });
        }, "Toji-Restore").start();
    }

    private void closeAll() {
        if (busy) {
            message("Aguarde o comando terminar.");
            return;
        }
        boolean active = getSharedPreferences(
                ResolutionController.PREF, MODE_PRIVATE).getBoolean("active", false);
        if (active) {
            restore(true);
        } else {
            testing = false;
            cancelTimer();
            stopSelf();
        }
    }

    private void openBlood() {
        try {
            Intent intent = getPackageManager().getLaunchIntentForPackage(
                    "com.netease.newspike");
            if (intent == null) {
                message("Blood Strike não encontrado.");
                return;
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            expand(false);
            startActivity(intent);
        } catch (Exception e) {
            message("Não foi possível abrir Blood Strike.");
        }
    }

    @Override public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        if (expanded) root.post(() -> expand(true));
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }

    @Override public void onDestroy() {
        boolean needsRestore = testing;
        destroyed = true;
        testing = false;
        cancelTimer();
        if (needsRestore) {
            new Thread(() -> ResolutionController.restore(getApplicationContext()),
                    "Toji-Restore-On-Exit").start();
        }
        if (wm != null && root != null) {
            try { wm.removeView(root); } catch (Exception ignored) { }
        }
        super.onDestroy();
    }
}
