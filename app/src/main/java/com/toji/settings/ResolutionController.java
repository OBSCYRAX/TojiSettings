
package com.toji.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;
import rikka.shizuku.Shizuku;

public final class ResolutionController {

    private ResolutionController() {}

    public static final String PREF = "toji_stretch";

    public static boolean authorized() {
        try {
            return Shizuku.pingBinder()
                && Shizuku.checkSelfPermission()
                == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
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

    public static Result execute(String... args) {
        if (!authorized()) {
            return new Result(false,
                "Shizuku não está conectado ou autorizado.");
        }

        Process process = null;

        try {
            String[] command = new String[args.length + 1];
            command[0] = "wm";
            System.arraycopy(args, 0, command, 1, args.length);

            process = Shizuku.newProcess(command, null, null);

            if (!process.waitFor(8, TimeUnit.SECONDS)) {
                process.destroy();
                return new Result(false, "Tempo limite do comando.");
            }

            StringBuilder output = new StringBuilder();

            BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()));

            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
            }

            BufferedReader errors = new BufferedReader(
                new InputStreamReader(process.getErrorStream()));

            while ((line = errors.readLine()) != null) {
                output.append(line).append('\n');
            }

            int exit = process.exitValue();

            return new Result(
                exit == 0,
                output.length() > 0
                    ? output.toString().trim()
                    : exit == 0
                        ? "Comando executado."
                        : "Falha no comando (" + exit + ")."
            );

        } catch (Throwable e) {
            return new Result(false,
                "Falha pelo Shizuku: " + e.getMessage());
        } finally {
            if (process != null) process.destroy();
        }
    }

    public static Result apply(Context context, int width, int height) {
        if (width < 600 || width > 3000 ||
            height < 600 || height > 4000) {
            return new Result(false, "Resolução inválida.");
        }

        Result current = execute("size");
        if (!current.ok) return current;

        SharedPreferences prefs = context.getSharedPreferences(
            PREF, Context.MODE_PRIVATE);

        if (!prefs.getBoolean("active", false)) {
            prefs.edit()
                .putString("before", current.message)
                .apply();
        }

        Result result = execute("size", width + "x" + height);
        if (!result.ok) return result;

        Result verify = execute("size");

        if (!verify.ok ||
            !verify.message.contains(width + "x" + height)) {
            execute("size", "reset");
            return new Result(false,
                "Não foi possível confirmar a resolução.");
        }

        prefs.edit()
            .putBoolean("active", true)
            .putInt("width", width)
            .putInt("height", height)
            .apply();

        return new Result(true,
            "Resolução aplicada: " + width + " × " + height);
    }

    public static Result restore(Context context) {
        Result result = execute("size", "reset");

        if (result.ok) {
            context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit()
                .putBoolean("active", false)
                .remove("width")
                .remove("height")
                .apply();
        }

        return result.ok
            ? new Result(true, "Resolução padrão restaurada.")
            : result;
    }
}
