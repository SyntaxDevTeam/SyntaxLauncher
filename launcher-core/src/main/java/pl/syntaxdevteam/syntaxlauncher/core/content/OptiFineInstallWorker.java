package pl.syntaxdevteam.syntaxlauncher.core.content;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;

/** Runs only in a disposable child JVM against a private Minecraft staging tree. */
public final class OptiFineInstallWorker {
    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("Expected installer, staging directory and version");
        try (URLClassLoader loader = new URLClassLoader(new URL[]{new File(args[0]).toURI().toURL()}, ClassLoader.getPlatformClassLoader())) {
            Class<?> installer = Class.forName("optifine.Installer", true, loader);
            String version = (String) installer.getMethod("getOptiFineVersion").invoke(null);
            if (!args[2].equals(version)) throw new IllegalArgumentException("OptiFine installer version mismatch");
            installer.getMethod("doInstall", File.class).invoke(null, new File(args[1]));
        }
    }
}
