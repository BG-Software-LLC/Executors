package com.bgsoftware.common.executors;

import com.google.common.base.Preconditions;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public class WorkerExecutor implements IExecutor<CompletableFuture<Integer>> {

    /**
     * The workers that the executor needs to run.
     * They are stored as a deque because the executor runs the workers in FIFO behavior
     * (First-in-first-out) - the first worker that was added is the first one to be executed.
     */
    private final Deque<IWorker> workers = new ArrayDeque<>();

    /**
     * The workers that have failed to be executed due to an unexpected error that were thrown
     * while running them. This collection can be retrieved using {@link #getFailedWorkers()}.
     */
    private final Deque<IWorker> failedWorkers = new ArrayDeque<>();

    /**
     * The associated bukkit-task of the executor.
     * The executor self-cancels itself, and therefore needs a copy of the {@link BukkitTask} so it
     * can cancel it once the executor finishes running all of its workers.
     */
    private BukkitTask associatedBukkitTask;

    /**
     * {@link CompletableFuture} representing when the executor finishes running all of its workers.
     * This object is returned upon running {@link #start(JavaPlugin)}, and is completed once the
     * executor finishes. The executor sets the value of the {@link CompletableFuture} to the amount of
     * successfully completed workers. You can get a list of failed workers using the {@link #getFailedWorkers()}
     * method.
     */
    private CompletableFuture<Integer> endExecutorFuture;

    /**
     * The maximum amount of time (in milliseconds) that each batch of workers can last.
     * The executor makes sure the time of each batch doesn't exceed this value, ensuring unnoticeable TPS loss.
     */
    private final int maximumMilliseconds;

    /**
     * The amount of workers that were successfully executed and completed.
     * This value is returned by {@link #endExecutorFuture} when the executor finishes executing
     * all of its workers.
     */
    private final AtomicInteger completedWorkers = new AtomicInteger(0);

    /**
     * WorkerExecutors are used to execute workers in batches, so the TPS of the server is not affected.
     * The workers that the executor needs to run are spread across multiple ticks, making it light-weight for
     * the server to execute. Each batch is configured to last a specific amount of time, and it cannot exceed
     * this time, making the potential TPS loss almost unnoticeable.
     * <p>
     * This executor is based on an article by @7smile7 on SpigotMC forums:
     * https://www.spigotmc.org/threads/409003/
     *
     * @param maximumMilliseconds The maximum amount of milliseconds a batch of workers can last.
     */
    public WorkerExecutor(int maximumMilliseconds) {
        this.maximumMilliseconds = maximumMilliseconds;
    }

    /**
     * Add a task to be executed by the executor.
     * <p>
     * Tasks are represented by {@link IWorker}, and should do one job for the best performance.
     *
     * @param worker The worker to add.
     */
    public void addWorker(IWorker worker) {
        this.workers.add(worker);
    }

    /**
     * Get the failed workers during the executor operation.
     */
    public Deque<IWorker> getFailedWorkers() {
        return this.failedWorkers;
    }

    /**
     * Start running the workers of the executor.
     *
     * The executor will run each batch of workers in separate ticks, and will make sure they last
     * at most {@link #maximumMilliseconds} milliseconds. The executor will end once all the workers
     * have done their job.
     *
     * @param plugin The plugin instance that called the executor.
     *
     * @return {@link CompletableFuture} object that will be completed once the executor finishes executing
     * all the workers given with {@link #addWorker(IWorker)}. For more information about the {@link CompletableFuture},
     * see {@link #endExecutorFuture}
     */
    @Override
    public CompletableFuture<Integer> start(JavaPlugin plugin) {
        Preconditions.checkState(this.endExecutorFuture == null, "You cannot start an executor that has an already on going task.");
        this.endExecutorFuture = new CompletableFuture<>();
        this.completedWorkers.set(0);
        this.associatedBukkitTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this, 0L, 1L);
        return endExecutorFuture;
    }

    /**
     * Stop the executor from running any longer.
     * The remaining executors that haven't finished can be retrieved using {@link #getFailedWorkers()}.
     * This executor can be started again using {@link #start(JavaPlugin)}.
     */
    @Override
    public void stop() {
        this.associatedBukkitTask.cancel();
        this.failedWorkers.addAll(this.workers);
        this.endExecutorFuture.complete(this.completedWorkers.get());
        this.endExecutorFuture = null;
    }

    /**
     * The main logic of the executor.
     * Do not run this method directly, unless you want to handle the loop on your own.
     * Otherwise, check {@link #start(JavaPlugin)} for starting this executor.
     *
     * The executor first calculates the time it needs to finish by taking the current time and adding to it
     * the value of {@link #maximumMilliseconds}. After that, it starts a while-loop that can end if the
     * time has exceeded, or if there are no workers left to execute. Each iteration, it looks for the next
     * worker and makes sure it is valid. If it is valid, it runs the worker and ends the iteration.
     * Once the while loop finishes, the executor checks if there are any workers left. If there are none,
     * it means the executor has finished, and therefore it cancels the {@link #associatedBukkitTask},
     * notifies {@link #endExecutorFuture} and sets it to null so the executor can be used again.
     */
    @Override
    public void run() {
        long finishTime = System.currentTimeMillis() + this.maximumMilliseconds;
        IWorker currentWorker = null;

        while (System.currentTimeMillis() < finishTime && (currentWorker = this.workers.poll()) != null) {
            try {
                currentWorker.work();
                this.completedWorkers.incrementAndGet();
            } catch (Throwable error) {
                this.failedWorkers.add(currentWorker);
            }
        }

        if (currentWorker == null) {
            this.stop();
        }
    }

}
