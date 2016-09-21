package com.github.games647.lagmonitor;

import com.google.common.collect.Sets;

import java.io.FilePermission;
import java.net.SocketPermission;
import java.security.Permission;
import java.util.Arrays;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public class BlockingSecurityManager extends SecurityManager {

    private final LagMonitor plugin;
    private final SecurityManager delegate;

    private final Set<String> violatedPlugins = Sets.newHashSet();

    public BlockingSecurityManager(LagMonitor plugin, SecurityManager delegate) {
        this.plugin = plugin;

        this.delegate = delegate;
    }

    public BlockingSecurityManager(LagMonitor plugin) {
        this(plugin, null);
    }

    public SecurityManager getOldSecurityManager() {
        return delegate;
    }

    @Override
    public void checkPermission(Permission perm, Object context) {
        if (delegate != null) {
            delegate.checkPermission(perm, context);
        }

        checkMainThreadOperation(perm);
    }

    @Override
    public void checkPermission(Permission perm) {
        if (delegate != null) {
            delegate.checkPermission(perm);
        }

        checkMainThreadOperation(perm);
    }

    private void checkMainThreadOperation(Permission perm) {
        if (Bukkit.isPrimaryThread() && isBlockingAction(perm)) {
            Exception stackTraceCreator = new Exception();
            StackTraceElement[] stackTrace = stackTraceCreator.getStackTrace();

            //remove the parts from LagMonitor
            StackTraceElement[] copyOfRange = Arrays.copyOfRange(stackTrace, 2, stackTrace.length);
            Entry<Plugin, StackTraceElement> foundPlugin = PluginUtil.findPlugin(copyOfRange);
            String pluginName = "unknown";
            if (foundPlugin != null) {
                pluginName = foundPlugin.getKey().getName();
                if (!violatedPlugins.add(pluginName) && plugin.getConfig().getBoolean("oncePerPlugin")) {
                    return;
                }
            }

            plugin.getLogger().log(Level.WARNING, "Plugin {0} is performing a blocking action on the main thread "
                    + "This could be a performance hit {1}. "
                    + "Report it to the plugin author", new Object[]{pluginName, perm});

            if (plugin.getConfig().getBoolean("hideStacktrace")) {
                if (foundPlugin != null) {
                    StackTraceElement source = foundPlugin.getValue();
                    plugin.getLogger().log(Level.WARNING, "Source: {0}, method {1}, line {2}"
                            , new Object[]{source.getClassName(), source.getMethodName(), source.getLineNumber()});
                }
            } else {
                plugin.getLogger().log(Level.WARNING, "", stackTraceCreator);
            }
        }
    }

    private boolean isBlockingAction(Permission permission) {
        String actions = permission.getActions();
        if (permission instanceof FilePermission) {
            //commented out, because also operations like .createNewFile() is also a write permission
            //which could executed by the main thread, doesn't it`?
//            ignore jar files because the java runtime load and unload classes at runtime
            return actions.contains("read") && !permission.getName().contains(".jar");
            //read write
        } else if (permission instanceof SocketPermission) {
            return actions.contains("connect");
        }

        return false;
    }
}
