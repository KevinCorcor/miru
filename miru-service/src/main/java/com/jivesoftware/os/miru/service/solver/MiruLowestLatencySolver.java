package com.jivesoftware.os.miru.service.solver;

import com.google.common.base.Optional;
import com.jivesoftware.os.miru.api.MiruPartitionCoord;
import com.jivesoftware.os.miru.plugin.solution.MiruPartitionResponse;
import com.jivesoftware.os.miru.plugin.solution.MiruSolution;
import com.jivesoftware.os.miru.plugin.solution.MiruSolutionLog;
import com.jivesoftware.os.miru.plugin.solution.MiruSolutionLogLevel;
import com.jivesoftware.os.miru.plugin.solution.MiruSolvable;
import com.jivesoftware.os.mlogger.core.MetricLogger;
import com.jivesoftware.os.mlogger.core.MetricLoggerFactory;
import com.jivesoftware.os.mlogger.core.ValueType;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * @author jonathan
 */
public class MiruLowestLatencySolver implements MiruSolver {

    private static final MetricLogger log = MetricLoggerFactory.getLogger();

    private final Executor executor;
    private final int initialSolvers;
    private final int maxNumberOfSolvers;
    private final long defaultAddAnotherSolverAfterNMillis;
    private final long failAfterNMillis;

    public MiruLowestLatencySolver(
        Executor executor,
        int initialSolvers,
        int maxNumberOfSolvers,
        long defaultAddAnotherSolverAfterNMillis,
        long failAfterNMillis) {
        this.executor = executor;
        this.initialSolvers = initialSolvers;
        this.maxNumberOfSolvers = maxNumberOfSolvers;
        this.defaultAddAnotherSolverAfterNMillis = defaultAddAnotherSolverAfterNMillis;
        this.failAfterNMillis = failAfterNMillis;
    }

    @Override
    public <R> MiruSolved<R> solve(String requestName,
        String queryKey,
        Iterator<MiruSolvable<R>> solvables,
        Optional<Long> suggestedTimeoutInMillis,
        MiruSolutionLog solutionLog)
        throws InterruptedException {

        long failAfterTime = System.currentTimeMillis() + failAfterNMillis;
        long addAnotherSolverAfterNMillis = suggestedTimeoutInMillis.or(defaultAddAnotherSolverAfterNMillis);

        CompletionService<MiruPartitionResponse<R>> completionService = new ExecutorCompletionService<>(executor);
        int solversAdded = 0;
        int solversSuccess = 0;
        int solversFailed = 0;
        long startTime = System.currentTimeMillis();
        List<SolvableFuture<R>> futures = new ArrayList<>(initialSolvers);
        List<MiruPartitionCoord> triedPartitions = new ArrayList<>(initialSolvers);
        MiruSolved<R> solved = null;
        try {
            log.set(ValueType.COUNT, "solve>timeout>" + requestName + ">" + queryKey, suggestedTimeoutInMillis.or(-1L));
            log.inc("solve>calls");
            log.inc("solve>calls>" + requestName + ">" + queryKey);
            while (solvables.hasNext() && solversAdded < initialSolvers) {
                MiruSolvable<R> solvable = solvables.next();
                solutionLog.log(MiruSolutionLogLevel.INFO, "Initial solver index={} coord={}", solversAdded, solvable.getCoord());
                triedPartitions.add(solvable.getCoord());
                futures.add(new SolvableFuture<>(solvable, completionService.submit(solvable), System.currentTimeMillis()));
                log.inc("solve>initial");
                log.inc("solve>initial>" + requestName + ">" + queryKey);
                solversAdded++;
            }
            while (solversFailed < maxNumberOfSolvers && System.currentTimeMillis() < failAfterTime) {
                boolean mayAddSolver = (solversAdded < maxNumberOfSolvers && solvables.hasNext());
                long timeout = Math.max(failAfterTime - System.currentTimeMillis(), 0);
                if (timeout == 0) {
                    solutionLog.log(MiruSolutionLogLevel.WARN, "WARNING: Ran out of time. Took more than {} millis to compute a solution.", failAfterTime);
                    break; // out of time
                }
                if (mayAddSolver) {
                    timeout = Math.min(timeout, addAnotherSolverAfterNMillis);
                }
                Future<MiruPartitionResponse<R>> future = completionService.poll(timeout, TimeUnit.MILLISECONDS);
                if (future != null) {
                    try {
                        MiruPartitionResponse<R> response = future.get();
                        if (response != null) {
                            // should be few enough of these that we prefer a linear lookup
                            for (SolvableFuture<R> f : futures) {
                                if (f.future == future) {
                                    solutionLog.log(MiruSolutionLogLevel.INFO, "Got a solution coord={}.", f.solvable.getCoord());
                                    long usedResultElapsed = System.currentTimeMillis() - f.startTime;
                                    long totalElapsed = System.currentTimeMillis() - startTime;
                                    solved = new MiruSolved<>(
                                        new MiruSolution(f.solvable.getCoord(),
                                            usedResultElapsed,
                                            totalElapsed,
                                            triedPartitions,
                                            response.log),
                                        response.answer);
                                    log.inc("solve>success");
                                    log.inc("solve>success>" + requestName + ">" + queryKey);
                                    log.incBucket("solve>throughput>success", 1_000L, 60);
                                    log.incBucket("solve>throughput>success>" + requestName + ">" + queryKey, 1_000L, 60);
                                    if (response.log != null) {
                                        for (String l : response.log) {
                                            solutionLog.log(MiruSolutionLogLevel.INFO, "[{}] {}", f.solvable.getCoord(), l);
                                        }
                                    }
                                    solversSuccess++;
                                    break;
                                }
                            }
                            if (solved == null) {
                                log.error("Unmatched future");
                                solutionLog.log(MiruSolutionLogLevel.ERROR, "Unmatched future.");
                            }
                            break;
                        } else {
                            solversFailed++;
                        }
                    } catch (ExecutionException e) {
                        log.debug("Solver failed to execute", e.getCause());
                        log.incBucket("solve>throughput>failure", 1_000L, 60);
                        log.incBucket("solve>throughput>failure>" + requestName + ">" + queryKey, 1_000L, 60);
                        solutionLog.log(MiruSolutionLogLevel.WARN, "WARNING: Solver failed to execute. cause: {}", e.getMessage());
                        solversFailed++;
                    }
                } else {
                    solutionLog.log(MiruSolutionLogLevel.WARN, "No solution completed within {} millis. Will add addition solver if possible.", timeout);
                }
                if (mayAddSolver) {
                    MiruSolvable<R> solvable = solvables.next();
                    solutionLog.log(MiruSolutionLogLevel.INFO, "Added a solver coord={}", solvable.getCoord());
                    log.inc("solve>added");
                    log.inc("solve>added>" + requestName + ">" + queryKey);
                    triedPartitions.add(solvable.getCoord());
                    futures.add(new SolvableFuture<>(solvable, completionService.submit(solvable), System.currentTimeMillis()));
                    solversAdded++;
                } else if (solversFailed == solversAdded) {
                    solutionLog.log(MiruSolutionLogLevel.ERROR, "All solvers failed to execute.");
                    break;
                }
            }
        } finally {
            for (SolvableFuture<R> f : futures) {
                f.future.cancel(true);
            }
            int abandoned = solversAdded - solversSuccess - solversFailed;
            log.incBucket("solve>throughput>abandoned", 1_000L, 60, abandoned);
            log.incBucket("solve>throughput>abandoned>" + requestName + ">" + queryKey, 1_000L, 60, abandoned);
        }

        return solved;
    }

    private static class SolvableFuture<R> {

        private final MiruSolvable<R> solvable;
        private final Future<MiruPartitionResponse<R>> future;
        private final long startTime;

        private SolvableFuture(MiruSolvable<R> solvable, Future<MiruPartitionResponse<R>> future, long startTime) {
            this.solvable = solvable;
            this.future = future;
            this.startTime = startTime;
        }
    }
}
