package com.jivesoftware.os.miru.plugin;

import com.google.common.base.Optional;
import com.jivesoftware.os.miru.api.activity.MiruPartitionId;
import com.jivesoftware.os.miru.api.base.MiruTenantId;
import com.jivesoftware.os.miru.plugin.solution.MiruAnswerEvaluator;
import com.jivesoftware.os.miru.plugin.solution.MiruAnswerMerger;
import com.jivesoftware.os.miru.plugin.solution.MiruPartitionResponse;
import com.jivesoftware.os.miru.plugin.solution.MiruResponse;
import com.jivesoftware.os.miru.plugin.solution.MiruSolutionLogLevel;
import com.jivesoftware.os.miru.plugin.solution.MiruSolvableFactory;

/**
 *
 */
public interface Miru {

    <Q, A, P> MiruResponse<A> askAndMerge(
        MiruTenantId tenantId,
        MiruSolvableFactory<Q, A, P> solvableFactory,
        MiruAnswerEvaluator<A> evaluator,
        MiruAnswerMerger<A> merger,
        A defaultValue,
        MiruSolutionLogLevel logLevel) throws Exception;

    <Q, A, P> MiruResponse<A> askAndMergePartition(
        MiruTenantId tenantId,
        MiruPartitionId partitionId,
        MiruSolvableFactory<Q, A, P> solvableFactory,
        MiruAnswerMerger<A> merger,
        A defaultValue,
        MiruSolutionLogLevel logLevel)
        throws Exception;

    <Q, A, P> MiruPartitionResponse<A> askImmediate(
        MiruTenantId tenantId,
        MiruPartitionId partitionId,
        MiruSolvableFactory<Q, A, P> factory,
        Optional<P> report,
        A defaultValue,
        MiruSolutionLogLevel logLevel) throws Exception;

}
