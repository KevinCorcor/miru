package com.jivesoftware.os.miru.service.stream.factory;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.jivesoftware.os.miru.api.MiruBackingStorage;
import com.jivesoftware.os.miru.api.MiruHost;
import com.jivesoftware.os.miru.api.MiruPartition;
import com.jivesoftware.os.miru.api.MiruPartitionCoord;
import com.jivesoftware.os.miru.api.MiruPartitionCoordInfo;
import com.jivesoftware.os.miru.api.MiruPartitionState;
import com.jivesoftware.os.miru.api.activity.MiruPartitionId;
import com.jivesoftware.os.miru.api.base.MiruTenantId;
import com.jivesoftware.os.miru.plugin.solution.MiruPartitionResponse;
import com.jivesoftware.os.miru.plugin.solution.MiruSolutionLog;
import com.jivesoftware.os.miru.plugin.solution.MiruSolvable;
import com.jivesoftware.os.miru.service.solver.MiruLowestLatencySolver;
import com.jivesoftware.os.miru.service.solver.MiruSolved;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

/**
 *
 */
public class MiruLowestLatencySolverTest {

    @Test
    public void testSolveForLowestLatency() throws Exception {
        Executor executor = Executors.newFixedThreadPool(10);
        int initialSolvers = 2;
        int maxNumberOfSolvers = 10;
        long addAnotherSolverAfterNMillis = 100;
        long failAfterNMillis = 3_000;

        MiruLowestLatencySolver solver = new MiruLowestLatencySolver(executor, initialSolvers, maxNumberOfSolvers,
            addAnotherSolverAfterNMillis, failAfterNMillis);

        List<MiruSolvable<Integer>> solvables = Lists.newArrayList();
        List<MiruPartition> orderedPartitions = Lists.newArrayList();
        for (int i = 0; i < 10; i++) {
            final int id = i;
            MiruPartitionCoord coord = new MiruPartitionCoord(new MiruTenantId("test".getBytes()),
                MiruPartitionId.of(1),
                new MiruHost("localhost", 49_600 + i));
            solvables.add(new MiruSolvable<>(
                coord,
                new Callable<MiruPartitionResponse<Integer>>() {
                    @Override
                    public MiruPartitionResponse<Integer> call() throws Exception {
                        Thread.sleep(id * 1_000); // Fake latency for each callable, 0 should always win
                        return new MiruPartitionResponse<>(id, null);
                    }
                }));
            orderedPartitions.add(new MiruPartition(coord, new MiruPartitionCoordInfo(MiruPartitionState.online, MiruBackingStorage.memory)));
        }

        Collections.shuffle(solvables, new Random(1_234)); // randomize the solvers
       
        MiruSolutionLog solutionLog = new MiruSolutionLog(false);
        MiruSolved<Integer> solved = solver.solve(solvables.iterator(), Optional.<Long>absent(), solutionLog);
        assertNotNull(solved.answer, "The answer was null, this probably means that the solver timed out when it shouldn't have.");
        assertEquals((int) solved.answer, 0);
        assertNotNull(solved.solution, "The solution was null");
        assertEquals(solved.solution.usedPartition.host.getPort(), 49_600);
    }
}
