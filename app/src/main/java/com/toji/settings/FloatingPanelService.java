package com.toji.settings;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Toji Settings – painel flutuante com barra de ajuste de altura.
 * A barra muda apenas o valor exibido. NUNCA executa wm size enquanto arrasta.
 * Compatível com ResolutionController da V3.2 (testConnection, apply, restore).
 */
public class FloatingPanelService extends Service {

    private static final int BACKGROUND = 0xff171c2b;
    private static final int PANEL_FIELD = 0xff2b3044;
    private static final int PURPLE = 0xffa444f4;
    private static final int PINK = 0xffff1687;
    private static final int WHITE = Color.WHITE;
    private static final int SLIDER_MIN_HEIGHT = 1600;
    private static final int SLIDER_MAX_HEIGHT = 3200;
    private static final int SLIDER_STEP = 20;
    private static final long SAFE_TEST_MS = 15000L;

    private WindowManager windowManager;
    private WindowManager.LayoutParams params;
    private LinearLayout root;
    private LinearLayout panel;
    private ScrollView panelScroll;
    private TextView bubble;
    private TextView status;
    private TextView sliderValue;
    private EditText widthInput;
    private EditText heightInput;
    private SeekBar heightSlider;

    private boolean expanded = false;
    private boolean busy = false;
    private Runnable pendingRestore;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private int dp(float amount) {
        return (int) (amount * getResources().getDisplayMetrics().density + .5f);
    }

    private GradientDrawable shape(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private TextView label(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private void addBlock(LinearLayout parent, View child, int marginTop) {
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, -2);
        layout.topMargin = dp(marginTop);
        parent.addView(child, layout);
    }

    private TextView button(String name, int color) {
        TextView view = label(name, 12, WHITE, true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(8), dp(11), dp(8), dp(11));
        view.setBackground(shape(color, 12));
        addBlock(panel, view, 8);
        return view;
    }

    private void toast(String message) {
        handler.post(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show());
    }

    private void setStatus(String message) {
        handler.post(() -> {
            if (status != null) status.setText(message);
        });
    }

    private void hideKeyboard() {
        try {
            InputMethodManager input = (InputMethodManager)
                    getSystemService(Context.INPUT_METHOD_SERVICE);
            if (input != null && root != null) {
                input.hideSoftInputFromWindow(root.getWindowToken(), 0);
            }
            if (root != null) root.clearFocus();
        } catch (Exception ignored) {
        }
    }

    private void updateWindow() {
        if (root == null || windowManager == null || params == null) return;
        try {
            windowManager.updateViewLayout(root, params);
        } catch (Exception ignored) {
        }
    }

    private void setExpanded(boolean open) {
        if (root == null || params == null) return;
        expanded = open;
        bubble.setVisibility(open ? View.GONE : View.VISIBLE);
        panelScroll.setVisibility(open ? View.VISIBLE : View.GONE);

        if (open) {
            params.width = dp(300);
            int screenHeight = getResources().getDisplayMetrics().heightPixels;
            params.height = Math.max(dp(180), Math.min(dp(470), (int) (screenHeight * .78f)));
            params.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        } else {
            hideKeyboard();
            params.width = dp(62);
            params.height = dp(62);
            params.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        }
        updateWindow();
    }

    private void cancelPendingRestore() {
        if (pendingRestore != null) {
            handler.removeCallbacks(pendingRestore);
            pendingRestore = null;
        }
    }

    private void runCommand(int mode, int width, int height, boolean temporary) {
        if (busy) {
            toast("Aguarde o comando anterior.");
            return;
        }
        if (mode != 0) cancelPendingRestore();
        busy = true;
        setStatus(mode == 0 ? "Testando Shizuku..." :
                mode == 2 ? "Restaurando resolução..." : "Aplicando resolução...");

        new Thread(() -> {
            ResolutionController.Result response;
            try {
                if (mode == 0) response = ResolutionController.testConnection();
                else if (mode == 2) response = ResolutionController.restore(this);
                else response = ResolutionController.apply(this, width, height);
            } catch (Throwable error) {
                response = new ResolutionController.Result(false,
                        "Erro: " + String.valueOf(error.getMessage()));
            }

            final ResolutionController.Result result = response;
            handler.post(() -> {
                busy = false;
                String message = (result.ok ? "✓ " : "✕ ") + result.message;
                setStatus(message);
                toast(message);

                if (mode == 1 && temporary && result.ok) {
                    setStatus("Teste de 15 segundos: a tela será restaurada automaticamente.");
                    pendingRestore = new Runnable() {
                        @Override public void run() {
                            // Em execução normal, busy será false. Se houver outro
                            // comando em andamento, aguarda antes de restaurar.
                            if (busy) {
                                handler.postDelayed(this, 400);
                                return;
                            }
                            pendingRestore = null;
                            runCommand(2, 0, 0, false);
                        }
                    };
                    handler.postDelayed(pendingRestore, SAFE_TEST_MS);
                }
            });
        }, "Toji-Shizuku-Command").start();
    }

    private int readInt(EditText input) throws NumberFormatException {
        return Integer.parseInt(input.getText().toString().trim());
    }

    private void applyFromInputs(boolean temporary) {
        try {
            int width = readInt(widthInput);
            int height = readInt(heightInput);
            if (width < 600 || width > 3000 || height < 600 || height > 4000) {
                toast("Resolução fora dos limites.");
                return;
            }
            hideKeyboard();
            runCommand(1, width, height, temporary);
        } catch (NumberFormatException exception) {
            toast("Digite números válidos.");
        }
    }

    private void enableDrag(View target) {
        target.setOnTouchListener(new View.OnTouchListener() {
            float startRawX, startRawY;
            int oldX, oldY;
            boolean moved;

            @Override public boolean onTouch(View v, MotionEvent e) {
                if (params == null) return false;
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startRawX = e.getRawX();
                        startRawY = e.getRawY();
                        oldX = params.x;
                        oldY = params.y;
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - startRawX;
                        float dy = e.getRawY() - startRawY;
                        if (Math.abs(dx) > dp(8) || Math.abs(dy) > dp(8)) moved = true;
                        if (moved) {
                            params.x = oldX + (int) dx;
                            params.y = oldY + (int) dy;
                            updateWindow();
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!moved) setExpanded(!expanded);
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        return true;
                    default:
                        return false;
                }
            }
        });
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (!Settings.canDrawOverlays(this)) {
            toast("Autorize a sobreposição de tela.");
            stopSelf();
            return START_NOT_STICKY;
        }
        if (root != null) return START_NOT_STICKY;

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        bubble = label("TOJI", 13, WHITE, true);
        bubble.setGravity(Gravity.CENTER);
        GradientDrawable bubbleBackground = shape(0xff261a38, 100);
        bubbleBackground.setStroke(dp(2), PURPLE);
        bubble.setBackground(bubbleBackground);
        root.addView(bubble, new LinearLayout.LayoutParams(dp(62), dp(62)));

        panelScroll = new ScrollView(this);
        panelScroll.setFillViewport(false);
        panelScroll.setVerticalScrollBarEnabled(true);
        panelScroll.setVisibility(View.GONE);
        root.addView(panelScroll, new LinearLayout.LayoutParams(-1, -1));

        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(14), dp(14), dp(16));
        panel.setBackground(shape(BACKGROUND, 16));
        panelScroll.addView(panel, new ScrollView.LayoutParams(-1, -2));

        TextView title = label("☰  TOJI • RESOLUTION CHANGER", 14, 0xffd9a6ff, true);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(dp(8), dp(6), dp(8), dp(8));
        addBlock(panel, title, 0);
        TextView tip = label("Arraste a barra para escolher a altura. A resolução muda só ao aplicar.",
                11, 0xffb4aac5, false);
        addBlock(panel, tip, 2);

        SharedPreferences pref = getSharedPreferences(ResolutionController.PREF, MODE_PRIVATE);
        int savedWidth = pref.getInt("width", 1200);
        int savedHeight = pref.getInt("height", 2400);

        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.HORIZONTAL);
        addBlock(panel, fields, 8);

        widthInput = new EditText(this);
        widthInput.setText(String.valueOf(savedWidth));
        widthInput.setHint("Largura");
        widthInput.setSingleLine(true);
        widthInput.setTextSize(17);
        widthInput.setTextColor(WHITE);
        widthInput.setHintTextColor(0xffa7a5b9);
        widthInput.setGravity(Gravity.CENTER);
        widthInput.setBackground(shape(PANEL_FIELD, 9));
        widthInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        widthInput.setSelectAllOnFocus(true);
        fields.addView(widthInput, new LinearLayout.LayoutParams(0, dp(48), 1));

        TextView times = label("  ×  ", 16, WHITE, true);
        times.setGravity(Gravity.CENTER);
        fields.addView(times);

        heightInput = new EditText(this);
        heightInput.setText(String.valueOf(savedHeight));
        heightInput.setHint("Altura");
        heightInput.setSingleLine(true);
        heightInput.setTextSize(17);
        heightInput.setTextColor(WHITE);
        heightInput.setHintTextColor(0xffa7a5b9);
        heightInput.setGravity(Gravity.CENTER);
        heightInput.setBackground(shape(PANEL_FIELD, 9));
        heightInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        heightInput.setSelectAllOnFocus(true);
        fields.addView(heightInput, new LinearLayout.LayoutParams(0, dp(48), 1));

        sliderValue = label("Altura: " + savedHeight, 12, 0xfff1c9ff, true);
        addBlock(panel, sliderValue, 14);

        heightSlider = new SeekBar(this);
        heightSlider.setMax((SLIDER_MAX_HEIGHT - SLIDER_MIN_HEIGHT) / SLIDER_STEP);
        int initial = Math.min(SLIDER_MAX_HEIGHT, Math.max(SLIDER_MIN_HEIGHT, savedHeight));
        heightSlider.setProgress((initial - SLIDER_MIN_HEIGHT) / SLIDER_STEP);
        heightSlider.setProgressTintList(ColorStateList.valueOf(PINK));
        heightSlider.setThumbTintList(ColorStateList.valueOf(PINK));
        addBlock(panel, heightSlider, 3);
        heightSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                int chosenHeight = SLIDER_MIN_HEIGHT + progress * SLIDER_STEP;
                heightInput.setText(String.valueOf(chosenHeight));
                sliderValue.setText("Altura: " + chosenHeight + " px");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { hideKeyboard(); }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        TextView range = label("1600 px                                      3200 px", 10, 0xffaaaaaf, false);
        range.setGravity(Gravity.CENTER);
        addBlock(panel, range, 0);

        TextView safe = button("TESTAR RESOLUÇÃO (15s)", PINK);
        TextView apply = button("APLICAR RESOLUÇÃO", PURPLE);
        TextView restore = button("RESTAURAR TELA", 0xff5d3f91);
        TextView test = button("TESTAR SHIZUKU", 0xff39364c);
        TextView game = button("ABRIR BLOOD STRIKE", 0xff39364c);
        TextView minimize = button("MINIMIZAR", 0xff39364c);
        TextView close = button("FECHAR PAINEL", 0xff39364c);

        status = label("Shizuku: teste a conexão antes de aplicar.", 11, 0xffd7c5e9, false);
        status.setPadding(0, dp(10), 0, 0);
        addBlock(panel, status, 4);

        safe.setOnClickListener(v -> applyFromInputs(true));
        apply.setOnClickListener(v -> applyFromInputs(false));
        restore.setOnClickListener(v -> {
            hideKeyboard();
            runCommand(2, 0, 0, false);
        });
        test.setOnClickListener(v -> runCommand(0, 0, 0, false));
        game.setOnClickListener(v -> {
            Intent launch = getPackageManager().getLaunchIntentForPackage("com.netease.newspike");
            if (launch == null) {
                toast("Blood Strike não encontrado.");
                return;
            }
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(launch);
                setExpanded(false);
            } catch (Exception exception) {
                toast("Abra o Blood Strike manualmente.");
            }
        });
        minimize.setOnClickListener(v -> setExpanded(false));
        close.setOnClickListener(v -> {
            if (pendingRestore != null) {
                toast("Teste temporário ativo. Aguarde a restauração ou toque em RESTAURAR TELA.");
                return;
            }
            stopSelf();
        });

        enableDrag(bubble);
        enableDrag(title);

        params = new WindowManager.LayoutParams(
                dp(62), dp(62),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = dp(12);
        params.y = dp(110);

        try {
            windowManager.addView(root, params);
        } catch (Exception exception) {
            root = null;
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    @Override public void onDestroy() {
        hideKeyboard();
        if (pendingRestore != null) {
            cancelPendingRestore();
            new Thread(() -> {
                try { ResolutionController.restore(getApplicationContext()); }
                catch (Throwable ignored) { }
            }, "Toji-Restore-On-Close").start();
        }
        if (root != null && windowManager != null) {
            try { windowManager.removeView(root); }
            catch (Exception ignored) { }
        }
        root = null;
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }
}
