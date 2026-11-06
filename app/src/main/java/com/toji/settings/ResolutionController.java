
package com.toji.settings;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.Parcel;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import rikka.shizuku.Shizuku;

/**
 * Toji Settings - controlador de resolucao.
 * V3.3: verifica a resolucao efetiva,
 * preserva overrides anteriores e avisa
 * sobre proporcoes arriscadas.
 */
public final class ResolutionController {
    private ResolutionController() {}

    public static final String PREF = "toji_stretch";
    private static final Object LOCK = new Object();
    private static final Object OPERATION_LOCK = new Object();

    private static volatile IBinder service;
    private static boolean binding;

    private static final Pattern PHYSICAL =
        Pattern.compile("(?m)^Physical size:\\s*(\\d{3,4}x\\d{3,4})\\s*$");

    private static final Pattern OVERRIDE =
        Pattern.compile("(?m)^Override size:\\s*(\\d{3,4}x\\d{3,4})\\s*$");

    private static final Shizuku.UserServiceArgs SERVICE_ARGS =
        new Shizuku.UserServiceArgs(
            new ComponentName(
                "com.toji.settings",
                ShellUserService.class.getName()
            )
        ).processNameSuffix("resolution")
         .daemon(false)
         .version(2);

    private static final ServiceConnection CONNECTION =
        new ServiceConnection() {
            @Override
            public void onServiceConnected(
                ComponentName name, IBinder binder
            ) {
                synchronized (LOCK) {
                    service = binder;
                    binding = false;
                    LOCK.notifyAll();
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                synchronized (LOCK) {
                    service = null;
                    binding = false;
                    LOCK.notifyAll();
                }
            }
        };

    public static final class Result {
        public final boolean ok;
        public final String message;

        public Result(boolean ok, String message) {
            this.ok = ok;
            this.message = message;
        }
    }

    public static Result status() {
        try {
            if (!Shizuku.pingBinder()) {
                return new Result(
                    false,
                    "Binder do Shizuku indisponivel. Ative o Shizuku e reabra o Toji."
                );
            }

            if (Shizuku.checkSelfPermission()
                != PackageManager.PERMISSION_GRANTED) {
                return new Result(
                    false,
                    "Shizuku ativo, mas sem permissao para o Toji."
                );
            }

            return new Result(
                true,
                "Shizuku conectado e autorizado."
            );
        } catch (Throwable t) {
            return new Result(
                false,
                "Erro Shizuku: "
                    + t.getClass().getSimpleName()
                    + ": " + t.getMessage()
            );
        }
    }

    public static boolean authorized() {
        return status().ok;
    }

    private static IBinder connect() throws Exception {
        synchronized (LOCK) {
            if (service != null && service.pingBinder()) {
                return service;
            }

            if (!binding) {
                binding = true;

                try {
                    Shizuku.bindUserService(
                        SERVICE_ARGS, CONNECTION
                    );
                } catch (Throwable e) {
                    binding = false;
                    LOCK.notifyAll();
                    throw new Exception(
                        "Falha ao vincular UserService", e
                    );
                }
            }

            long deadline =
                android.os.SystemClock.elapsedRealtime()
                + 12000;

            while (
                binding &&
                (service == null || !service.pingBinder())
            ) {
                long left = deadline -
                    android.os.SystemClock.elapsedRealtime();

                if (left <= 0) break;
                LOCK.wait(left);
            }

            if (service == null || !service.pingBinder()) {
                binding = false;
                throw new Exception(
                    "UserService nao conectou em 12 segundos. Confira o Logcat."
                );
            }

            return service;
        }
    }

    public static Result execute(String... args) {
        Result auth = status();
        if (!auth.ok) return auth;

        if (android.os.Looper.myLooper()
            == android.os.Looper.getMainLooper()) {
            return new Result(
                false,
                "Execute o comando fora da thread principal."
            );
        }

        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();

        try {
            IBinder binder = connect();
            data.writeStringArray(args);

            if (!binder.transact(1, data, reply, 0)) {
                return new Result(
                    false,
                    "UserService recusou transacao."
                );
            }

            reply.readException();

            boolean ok = reply.readInt() == 1;
            String message = reply.readString();

            return new Result(
                ok,
                message == null ? "" : message
            );
        } catch (Throwable e) {
            synchronized (LOCK) {
                service = null;
                binding = false;
                LOCK.notifyAll();
            }

            return new Result(
                false,
                "Falha no UserService: "
                    + e.getClass().getSimpleName()
                    + ": " + e.getMessage()
            );
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    public static Result testConnection() {
        return execute("size");
    }

    private static String match(
        Pattern pattern, String output
    ) {
        if (output == null) return null;

        Matcher m = pattern.matcher(output);
        return m.find() ? m.group(1) : null;
    }

    private static String effectiveSize(String output) {
        String overridden = match(OVERRIDE, output);

        return overridden != null
            ? overridden
            : match(PHYSICAL, output);
    }

    private static Result restoreSnapshot(
        String snapshot
    ) {
        String overridden = match(OVERRIDE, snapshot);

        return overridden == null
            ? execute("size", "reset")
            : execute("size", overridden);
    }

    private static String ratioWarning(
        String snapshot,
        int width,
        int height
    ) {
        String physical = match(PHYSICAL, snapshot);
        if (physical == null) return "";

        String[] parts = physical.split("x");

        try {
            int w = Integer.parseInt(parts[0]);
            int h = Integer.parseInt(parts[1]);

            double nativeRatio =
                (double) Math.min(w, h) / Math.max(w, h);

            double targetRatio =
                (double) Math.min(width, height)
                / Math.max(width, height);

            if (Math.abs(nativeRatio - targetRatio)
                / nativeRatio <= 0.025) {
                return "";
            }

            int suggestedHeight = (int) Math.round(
                (double) width / nativeRatio
            );

            return " AVISO: proporcao diferente da tela fisica; "
                + "toques podem ficar fora do lugar. "
                + "Teste primeiro "
                + width + "x" + suggestedHeight
                + " (mesma proporcao).";

        } catch (RuntimeException e) {
            return "";
        }
    }

    public static Result apply(
        Context context,
        int width,
        int height
    ) {
        if (
            width < 600 || width > 3000 ||
            height < 600 || height > 4000
        ) {
            return new Result(
                false,
                "Resolucao fora dos limites."
            );
        }

        synchronized (OPERATION_LOCK) {
            Result before = execute("size");

            if (!before.ok) return before;

            if (effectiveSize(before.message) == null) {
                return new Result(
                    false,
                    "Nao foi possivel identificar a resolucao atual. Alteracao cancelada."
                );
            }

            SharedPreferences prefs =
                context.getSharedPreferences(
                    PREF,
                    Context.MODE_PRIVATE
                );

            if (!prefs.getBoolean("active", false)) {
                if (!prefs.edit()
                    .putString("before", before.message)
                    .commit()) {
                    return new Result(
                        false,
                        "Nao foi possivel guardar a resolucao anterior."
                    );
                }
            }

            String requested = width + "x" + height;

            Result applied = execute("size", requested);

            if (!applied.ok) {
                Result rollback =
                    restoreSnapshot(before.message);

                return new Result(
                    false,
                    rollback.ok
                        ? "Falha ao aplicar resolucao. Estado anterior restaurado: "
                            + applied.message
                        : "Falha ao aplicar e ao restaurar: "
                            + applied.message + " / "
                            + rollback.message
                );
            }

            Result verify = execute("size");

            if (!verify.ok ||
                !requested.equals(
                    effectiveSize(verify.message)
                )) {
                Result rollback =
                    restoreSnapshot(before.message);

                return new Result(
                    false,
                    rollback.ok
                        ? "Falha ao confirmar resolucao. Estado anterior restaurado."
                        : "Falha ao confirmar resolucao e ao reverter: "
                            + rollback.message
                );
            }

            prefs.edit()
                .putBoolean("active", true)
                .putInt("width", width)
                .putInt("height", height)
                .commit();

            return new Result(
                true,
                "Resolucao aplicada: "
                    + width + " x " + height + "."
                    + ratioWarning(
                        before.message, width, height
                    )
            );
        }
    }

    public static Result restore(Context context) {
        synchronized (OPERATION_LOCK) {
            SharedPreferences prefs =
                context.getSharedPreferences(
                    PREF,
                    Context.MODE_PRIVATE
                );

            String original =
                prefs.getString("before", null);

            Result r = restoreSnapshot(original);
            if (!r.ok) return r;

            Result verify = execute("size");

            String expectedOverride =
                match(OVERRIDE, original);

            String currentOverride = verify.ok
                ? match(OVERRIDE, verify.message)
                : null;

            if (
                !verify.ok ||
                (expectedOverride == null
                    ? currentOverride != null
                    : !expectedOverride.equals(
                        currentOverride
                    ))
            ) {
                return new Result(
                    false,
                    "Restauracao enviada, mas sem confirmacao. Verifique wm size."
                );
            }

            prefs.edit()
                .putBoolean("active", false)
                .remove("before")
                .remove("width")
                .remove("height")
                .commit();

            return new Result(
                true,
                expectedOverride == null
                    ? "Resolucao fisica padrao restaurada."
                    : "Resolucao que estava ativa antes do Toji restaurada."
            );
        }
    }
}
