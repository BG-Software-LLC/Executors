package com.bgsoftware.common.executors;

import org.bukkit.plugin.java.JavaPlugin;

public interface IExecutor<R> extends Runnable {

    /**
     * Starts the associated executor.
     *
     * @param plugin The plugin that started the executor.
     * @return The associated return value with this executor.
     */
    R start(JavaPlugin plugin);

}
