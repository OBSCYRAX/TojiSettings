
package com.toji.settings;

import android.app.Service;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.ColorStateList;
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

    private static final int PURPLE = 0xFF7844ED;
    private static final int DARK = 0xFF111827;
    private static final int BUTTON = 0xFF273347;
    private static final int TEST_MS = 15000;

    private WindowManager wm;
    private WindowManager.LayoutParams params;
    private LinearLayout root, header, content;
    private ScrollView scroll;
    private TextView name, info, barInfo;
    private EditText widthInput, heightInput;
    private SeekBar heightBar;

    private final Handler main =
        new Handler(Looper.getMainLooper());

    private Runnable restoreTimer;
    private boolean expanded = false;
    private boolean busy = false;
    private boolean testing = false;
    private boolean destroyed = false;

    private int bubbleX, bubbleY;

    private int dp(int value) {
        return Math.round(
            value * getResources()
                .getDisplayMetrics().density
        );
    }

    private GradientDrawable rounded(
        int color, int radius
    ) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(radius));
        return bg;
    }

    private TextView text(
        String value, int size, int color
    ) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private void addButton(
        String title, int color, Runnable click
    ) {
        TextView btn = text(title, 13, Color.WHITE);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(
            dp(8), dp(11), dp(8), dp(11)
        );
        btn.setBackground(rounded(color, 12));

        LinearLayout.LayoutParams lp =
            new LinearLayout.LayoutParams(-1, -2);

        lp.bottomMargin = dp(7);
        content.addView(btn, lp);
        btn.setOnClickListener(v -> click.run());
    }

    private EditText numberField(String defaultValue) {
        EditText field = new EditText(this);
        field.setText(defaultValue);
        field.setSingleLine(true);
        field.setSelectAllOnFocus(true);
        field.setInputType(InputType.TYPE_CLASS_NUMBER);
        field.setTextColor(Color.WHITE);
        field.setTextSize(15);
        field.setGravity(Gravity.CENTER);
        field.setBackground(rounded(BUTTON, 10));

        field.setOnTouchListener((v, event) -> {
            if (
                event.getActionMasked()
                == MotionEvent.ACTION_DOWN
            ) {
                setWindowFocusable(true);
            }
            return false;
        });

        field.setOnClickListener(v -> {
            setWindowFocusable(true);
            field.requestFocus();

            InputMethodManager imm =
                (InputMethodManager)
                getSystemService(INPUT_METHOD_SERVICE);

            if (imm != null) {
                imm.showSoftInput(
                    field,
                    InputMethodManager.SHOW_IMPLICIT
                );
            }
        });

        return field;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }

        wm = (WindowManager)
            getSystemService(WINDOW_SERVICE);

        int type = Build.VERSION.SDK_INT >= 26
            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            : WindowManager.LayoutParams.TYPE_PHONE;

        params = new WindowManager.LayoutParams(
            -2,
            -2,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = dp(12);
        params.y = dp(120);

        bubbleX = params.x;
        bubbleY = params.y;

        params.softInputMode =
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;

        buildInterface();

        try {
            wm.addView(root, params);
        } catch (Exception e) {
            stopSelf();
        }
    }

    private void buildInterface() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(
            dp(4), dp(4), dp(8), dp(4)
        );
        header.setBackground(
            rounded(0xFF151827, 22)
        );

        TextView bubble = text(
            "T", 19, Color.WHITE
        );
        bubble.setGravity(Gravity.CENTER);
        bubble.setTypeface(null, 1);
        bubble.setBackground(
            rounded(PURPLE, 30)
        );

        header.addView(
            bubble,
            new LinearLayout.LayoutParams(
                dp(44), dp(44)
            )
        );

        // Puxador visivel mesmo minimizado.
        TextView grip = text(
            "  ━━", 16, 0xFFFF78C7
        );
        header.addView(grip);

        name = text(
            "  TOJI  |  STRETCH",
            12,
            Color.WHITE
        );
        name.setTypeface(null, 1);
        name.setVisibility(View.GONE);
        header.addView(name);

        root.addView(header);
        attachDrag(header);

        scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(true);
        scroll.setBackground(
            rounded(DARK, 16)
        );
        scroll.setVisibility(View.GONE);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(
            dp(12), dp(12), dp(12), dp(8)
        );

        scroll.addView(content);
        root.addView(scroll);

        TextView heading = text(
            "RESOLUÇÃO",
            16,
            Color.WHITE
        );
        heading.setTypeface(null, 1);
        heading.setPadding(
            0, 0, 0, dp(10)
        );
        content.addView(heading);

        LinearLayout fields =
            new LinearLayout(this);
        fields.setOrientation(
            LinearLayout.HORIZONTAL
        );

        widthInput = numberField("1080");
        heightInput = numberField("1920");

        LinearLayout.LayoutParams first =
            new LinearLayout.LayoutParams(
                0, dp(45), 1f
            );
        first.rightMargin = dp(7);

        fields.addView(widthInput, first);
        fields.addView(
            heightInput,
            new LinearLayout.LayoutParams(
                0, dp(45), 1f
            )
        );

        content.addView(fields);

        TextView hint = text(
            "LARGURA                         ALTURA",
            10,
            0xFFACB5C4
        );
        hint.setPadding(
            0, dp(4), 0, dp(11)
        );
        content.addView(hint);

        // Barrinha de ajuste de altura.
        barInfo = text(
            "BARRINHA • ALTURA: 1920",
            12,
            0xFFFF80C9
        );
        content.addView(barInfo);

        heightBar = new SeekBar(this);
        heightBar.setMax(80);
        heightBar.setProgress(16);

        if (Build.VERSION.SDK_INT >= 21) {
            heightBar.setProgressTintList(
                ColorStateList.valueOf(
                    0xFFFF65BB
                )
            );
            heightBar.setThumbTintList(
                ColorStateList.valueOf(
                    0xFFFF65BB
                )
            );
        }

        content.addView(heightBar);

        heightBar.setOnSeekBarChangeListener(
            new SeekBar.OnSeekBarChangeListener() {

                @Override
                public void onProgressChanged(
                    SeekBar bar,
                    int value,
                    boolean user
                ) {
                    int height = 1600 + value * 20;

                    barInfo.setText(
                        "BARRINHA • ALTURA: " + height
                    );

                    if (user) {
                        heightInput.setText(
                            String.valueOf(height)
                        );
                    }
                }

                @Override
                public void onStartTrackingTouch(
                    SeekBar bar
                ) {}

                @Override
                public void onStopTrackingTouch(
                    SeekBar bar
                ) {}
            }
        );

        // Botoes principais.
        addButton(
            "TESTAR RESOLUÇÃO",
            PURPLE,
            () -> {
                try {
                    int w = Integer.parseInt(
                        widthInput.getText()
                            .toString().trim()
                    );

                    int h = Integer.parseInt(
                        heightInput.getText()
                            .toString().trim()
                    );

                    applyResolution(w, h);

                } catch (NumberFormatException e) {
                    setStatus(
                        "Digite largura e altura válidas."
                    );
                }
            }
        );

        addButton(
            "MANTER RESOLUÇÃO",
            BUTTON,
            () -> {
                if (busy || !testing) {
                    setStatus(
                        "Nenhum teste esperando confirmação."
                    );
                    return;
                }

                testing = false;
                cancelRestoreTimer();

                setStatus(
                    "Mantida. Use RESTAURAR para voltar."
                );
            }
        );

        addButton(
            "RESTAURAR TELA",
            BUTTON,
            () -> restoreResolution(false)
        );

        addButton(
            "ABRIR BLOOD STRIKE",
            BUTTON,
            this::launchBloodStrike
        );

        addButton(
            "MINIMIZAR PARA BOLINHA",
            BUTTON,
            () -> setExpanded(false)
        );

        addButton(
            "FECHAR TUDO E RESTAURAR",
            0xFF71364B,
            this::closeEverything
        );

        info = text(
            "Pronto para testar.",
            11,
            0xFFC2B5FF
        );
        info.setPadding(
            0, dp(7), 0, dp(3)
        );
        content.addView(info);
    }

    private void attachDrag(View view) {
        final int slop =
            ViewConfiguration.get(this)
                .getScaledTouchSlop();

        view.setOnTouchListener(
            new View.OnTouchListener() {
                float downX, downY;
                int startX, startY;
                boolean moved;

                @Override
                public boolean onTouch(
                    View v, MotionEvent e
                ) {
                    switch (e.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            downX = e.getRawX();
                            downY = e.getRawY();

                            startX = params.x;
                            startY = params.y;

                            moved = false;
                            return true;

                        case MotionEvent.ACTION_MOVE:
                            int dx = Math.round(
                                e.getRawX() - downX
                            );
                            int dy = Math.round(
                                e.getRawY() - downY
                            );

                            if (
                                Math.abs(dx) > slop ||
                                Math.abs(dy) > slop
                            ) {
                                moved = true;
                            }

                            if (moved) {
                                params.x = Math.max(
                                    0, startX + dx
                                );
                                params.y = Math.max(
                                    0, startY + dy
                                );

                                if (!expanded) {
                                    bubbleX = params.x;
                                    bubbleY = params.y;
                                }

                                updateOverlay();
                            }
                            return true;

                        case MotionEvent.ACTION_UP:
                            if (!moved) {
                                setExpanded(!expanded);
                            }
                            return true;

                        case MotionEvent.ACTION_CANCEL:
                            return true;

                        default:
                            return false;
                    }
                }
            }
        );
    }

    private void setWindowFocusable(boolean yes) {
        if (params == null) return;

        if (yes) {
            params.flags &=
                ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        } else {
            params.flags |=
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        }

        params.flags |=
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;

        updateOverlay();
    }

    private void hideKeyboard() {
        if (root == null) return;

        InputMethodManager imm =
            (InputMethodManager)
            getSystemService(INPUT_METHOD_SERVICE);

        if (imm != null) {
            imm.hideSoftInputFromWindow(
                root.getWindowToken(), 0
            );
        }

        root.clearFocus();
        setWindowFocusable(false);
    }

    private void setExpanded(boolean open) {
        if (root == null || scroll == null) return;

        hideKeyboard();

        if (open && !expanded) {
            bubbleX = params.x;
            bubbleY = params.y;
        }

        expanded = open;

        name.setVisibility(
            open ? View.VISIBLE : View.GONE
        );
        scroll.setVisibility(
            open ? View.VISIBLE : View.GONE
        );

        if (open) {
            int screenW =
                getResources()
                    .getDisplayMetrics().widthPixels;

            int screenH =
                getResources()
                    .getDisplayMetrics().heightPixels;

            int width = Math.min(
                dp(285),
                Math.max(
                    dp(180),
                    screenW - dp(28)
                )
            );

            int height = Math.max(
                dp(100),
                Math.min(
                    dp(350),
                    screenH - dp(95)
                )
            );

            scroll.setLayoutParams(
                new LinearLayout.LayoutParams(
                    width, height
                )
            );

            header.setLayoutParams(
                new LinearLayout.LayoutParams(
                    width, -2
                )
            );

            params.x = Math.max(
                0,
                Math.min(
                    params.x,
                    screenW - width - dp(4)
                )
            );

            params.y = dp(5);

        } else {
            header.setLayoutParams(
                new LinearLayout.LayoutParams(
                    -2, -2
                )
            );

            params.x = bubbleX;
            params.y = bubbleY;
        }

        updateOverlay();
    }

    private void updateOverlay() {
        if (
            wm == null ||
            root == null ||
            root.getWindowToken() == null
        ) {
            return;
        }

        try {
            wm.updateViewLayout(root, params);
        } catch (Exception ignored) {
        }
    }

    private void setStatus(String message) {
        if (info != null) {
            info.setText(message);
        }
    }

    private void cancelRestoreTimer() {
        if (restoreTimer != null) {
            main.removeCallbacks(restoreTimer);
            restoreTimer = null;
        }
    }

    private void applyResolution(int width, int height) {
        if (busy || testing) {
            setStatus(
                "Confirme ou restaure o teste anterior."
            );
            return;
        }

        if (
            width < 600 || width > 3000 ||
            height < 600 || height > 4000
        ) {
            setStatus(
                "Resolução fora dos limites."
            );
            return;
        }

        hideKeyboard();
        busy = true;
        setStatus("Aplicando via Shizuku...");

        new Thread(() -> {
            ResolutionController.Result result =
                ResolutionController.apply(
                    getApplicationContext(),
                    width,
                    height
                );

            if (destroyed) {
                if (result.ok) {
                    ResolutionController.restore(
                        getApplicationContext()
                    );
                }
                return;
            }

            main.post(() -> {
                if (destroyed) return;

                busy = false;
                setStatus(result.message);

                if (!result.ok) return;

                testing = true;
                cancelRestoreTimer();

                setStatus(
                    "Teste ativo: 15 s para manter ou restaurar."
                );

                restoreTimer = () -> {
                    if (testing && !busy) {
                        restoreResolution(false);
                    }
                };

                main.postDelayed(
                    restoreTimer,
                    TEST_MS
                );
            });

        }, "Toji-Apply").start();
    }

    private void restoreResolution(boolean closeAll) {
        if (busy) {
            setStatus("Aguarde o comando terminar.");
            return;
        }

        hideKeyboard();

        busy = true;
        testing = false;

        cancelRestoreTimer();
        setStatus("Restaurando a resolução...");

        new Thread(() -> {
            ResolutionController.Result result =
                ResolutionController.restore(
                    getApplicationContext()
                );

            main.post(() -> {
                if (destroyed) return;

                busy = false;
                setStatus(result.message);

                if (result.ok && closeAll) {
                    stopSelf();
                }
            });

        }, "Toji-Restore").start();
    }

    private void closeEverything() {
        if (busy) {
            setStatus("Aguarde o comando terminar.");
            return;
        }

        boolean changedByToji =
            getSharedPreferences(
                Resolution
