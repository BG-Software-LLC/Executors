package com.bgsoftware.common.executors;

import org.bukkit.scheduler.BukkitTask;

public interface ITimerCreator {

    BukkitTask createTimer(Runnable runnable, long delay, long interval);

}
