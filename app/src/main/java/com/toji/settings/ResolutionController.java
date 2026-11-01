
package com.toji.settings;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.Parcel;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import rikka.shizuku.Shizuku;

public final class ResolutionController {

    private ResolutionController() {}

    public static final String PREF = "toji_stretch";

    private static final Object LOCK = new Object();
    private static volatile IBinder service;
    private static boolean binding = false;

    private static final ServiceConnection CONNECTION =
            new ServiceConnection() {
        @Override
        public void onServiceConnected(
                ComponentName name, IBinder binder) {
            service = binder;
            synchronized (LOCK) {
                binding = false;
                LOCK.notifyAll();
            }
        }

        @Override
        public void onServiceDisconnected(
                ComponentName name) {
            service = null;
            synchronized (LOCK) {
                binding = false;
                LOCK.notifyAll();
            }
        }
    };

    public static boolean authorized() {
        try {
            return Shizuku.pingBinder()
                    && Shizuku.checkSelfPermission()
                    == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable e) {
            return false;
        }
    }

    public static final class Result {
        public final boolean ok;
        public final String message;

        Result(boolean ok, String message) {
            this.ok = ok;
            this.message = message;
        }
    }

    private static IBinder connect() throws Exception {
        if (service != null && service.pingBinder()) {
            return service;
        }

        synchronized (LOCK) {
            if (!binding) {
                binding = true;

                Shizuku.UserServiceArgs args =
                        new Shizuku.UserServiceArgs(
                                new ComponentName(
                                        "com.toji.settings",
                                        ShellUserService.class.getName()
                                )
                        )
                        .processNameSuffix("resolution")
                        .daemon(false)
                        .version(1);

                try {
                    Shizuku.bindUserService(args, CONNECTION);
                } catch (Throwable e) {
                    binding = false;
                    LOCK.notifyAll();
                    throw e;
                }
            }

            long deadline =
                    System.currentTimeMillis() + 10000;

            while ((service == null
                    || !service.pingBinder())
                    && binding) {

                long remaining =
                        deadline - System.currentTimeMillis();

                if (remaining <= 0) {
                    break;
                }

                LOCK.wait(remaining);
            }

            if (service == null || !service.pingBinder()) {
                throw new Exception(
                        "Não foi possível conectar ao serviço Shizuku."
                );
            }

            return service;
        }
    }

    public static Result execute(String... args) {
        if (!authorized()) {
            return new Result(
                    false,
                    "Shizuku não está conectado ou autorizado."
            );
        }

        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();

        try {
            IBinder binder = connect();

            data.writeStringArray(args);

            boolean handled = binder.transact(
                    1, data, reply, 0
            );

            if (!handled) {
                return new Result(
                        false,
                        "O serviço não aceitou o comando."
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
            service = null;

            return new Result(
                    false,
                    "Falha pelo Shizuku: " + e.getMessage()
            );
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    public static Result apply(
            Context context, int width, int height) {

        if (width < 600 || width > 3000
                || height < 600 || height > 4000) {
            return new Result(
                    false, "Resolução inválida."
            );
        }

        Result current = execute("size");

        if (!current.ok) {
            return current;
        }

        SharedPreferences prefs =
                context.getSharedPreferences(
                        PREF, Context.MODE_PRIVATE
                );

        if (!prefs.getBoolean("active", false)) {
            prefs.edit()
                    .putString("before", current.message)
                    .apply();
        }

        Result result = execute(
                "size", width + "x" + height
        );

        if (!result.ok) {
            return result;
        }

        Result verify = execute("size");

        if (!verify.ok
                || !verify.message.contains(
                        width + "x" + height)) {
            execute("size", "reset");

            return new Result(
                    false,
                    "Não foi possível confirmar a resolução."
            );
        }

        prefs.edit()
                .putBoolean("active", true)
                .putInt("width", width)
                .putInt("height", height)
                .apply();

        return new Result(
                true,
                "Resolução aplicada: "
                        + width + " × " + height
        );
    }

    public static Result restore(Context context) {
        Result result = execute("size", "reset");

        if (result.ok) {
            context.getSharedPreferences(
                    PREF, Context.MODE_PRIVATE
            ).edit()
                    .putBoolean("active", false)
                    .remove("width")
                    .remove("height")
                    .apply();
        }

        return result.ok
                ? new Result(
                        true,
                        "Resolução padrão restaurada."
                )
                : result;
    }
}
