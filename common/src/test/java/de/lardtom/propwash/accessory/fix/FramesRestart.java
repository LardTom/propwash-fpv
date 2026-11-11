package de.lardtom.propwash.accessory.fix;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

final class FramesRestart {
    private FramesRestart() {
    }

    static String run(String phase, String input) {
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        try (URLClassLoader loader = new URLClassLoader("propwash-frames-" + phase, classpath(), ClassLoader.getPlatformClassLoader())) {
            thread.setContextClassLoader(loader);
            Method method = Class.forName(FramesWorld.class.getName(), true, loader).getMethod(phase, String.class);
            return (String) method.invoke(null, input);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Error error) {
                throw error;
            }
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException(phase, cause);
        } catch (ReflectiveOperationException | IOException e) {
            throw new IllegalStateException(phase, e);
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    private static URL[] classpath() throws MalformedURLException {
        Set<URL> urls = new LinkedHashSet<>();
        for (ClassLoader loader = FramesRestart.class.getClassLoader(); loader != null; loader = loader.getParent()) {
            if (loader instanceof URLClassLoader withUrls) {
                urls.addAll(Arrays.asList(withUrls.getURLs()));
            }
        }
        for (String entry : System.getProperty("java.class.path", "").split(File.pathSeparator)) {
            if (!entry.isBlank()) {
                urls.add(Path.of(entry).toUri().toURL());
            }
        }
        return urls.toArray(URL[]::new);
    }
}
