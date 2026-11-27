package pl.syntaxdevteam.syntaxlauncher.presence;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class GamePresence {
    private GamePresence() {}

    public static void initialize() {
        String output = System.getProperty("syntaxlauncher.presenceFile");
        if (output == null) return;
        try {
            // Supported Minecraft releases expose named client classes. Resolve once so a
            // missing game API disables the bridge without stopping Minecraft.
            Class<?> minecraft = Class.forName("net.minecraft.client.Minecraft");
            Class<?> serverData = Class.forName("net.minecraft.client.multiplayer.ServerData");
            ClientView view = new ClientView(minecraft.getMethod("getInstance"), minecraft.getField("player"),
                minecraft.getMethod("isLocalServer"), minecraft.getMethod("getCurrentServer"),
                serverData.getMethod("type"), serverData.getField("ip"));
            Path target = Path.of(output);
            Thread worker = new Thread(() -> publish(target, view), "SyntaxLauncher game presence");
            worker.setDaemon(true);
            worker.start();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            // The launcher falls back to its process-only activity.
        }
    }

    private static void publish(Path target, ClientView view) {
        AtomicReference<String> observed = new AtomicReference<>();
        AtomicBoolean queued = new AtomicBoolean();
        try {
            Path active = target.resolveSibling("active");
            while (Files.isRegularFile(active)) {
                Object client = view.getInstance().invoke(null);
                if (client instanceof Executor executor && queued.compareAndSet(false, true)) {
                    executor.execute(() -> {
                        try {
                            observed.set(view.snapshot(client));
                        } catch (ReflectiveOperationException | RuntimeException error) {
                            observed.set(null);
                        } finally {
                            queued.set(false);
                        }
                    });
                }
                Thread.sleep(600);
                String snapshot = observed.getAndSet(null);
                if (snapshot != null && Files.isRegularFile(active)) write(target, snapshot);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (ReflectiveOperationException | IOException | RuntimeException | LinkageError ignored) {
            // Output can disappear when the launcher closes.
        }
    }

    private static void write(Path target, String snapshot) throws IOException {
        Path temp = target.resolveSibling("activity.tmp");
        try {
            Files.writeString(temp, snapshot);
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private record ClientView(Method getInstance, Field player, Method localServer, Method currentServer,
                              Method serverType, Field serverAddress) {
        String snapshot(Object client) throws ReflectiveOperationException {
            if (player.get(client) == null) return "menu";
            if (Boolean.TRUE.equals(localServer.invoke(client))) return "singleplayer";
            Object server = currentServer.invoke(client);
            if (server != null && serverType.invoke(server) instanceof Enum<?> type && type.name().equals("OTHER")
                && serverAddress.get(server) instanceof String address && address.length() <= 253) {
                return "multiplayer\n" + address;
            }
            return "multiplayer";
        }
    }
}
