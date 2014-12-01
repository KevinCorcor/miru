package com.jivesoftware.os.miru.reco.plugins.trending;

import com.jivesoftware.os.miru.plugin.solution.MiruAnswerEvaluator;
import com.jivesoftware.os.miru.plugin.solution.MiruSolutionLog;

/**
 *
 */
public class TrendingAnswerEvaluator implements MiruAnswerEvaluator<OldTrendingAnswer> {

    @Override
    public boolean isDone(OldTrendingAnswer answer, MiruSolutionLog solutionLog) {
        solutionLog.log("Results exhausted = {}", answer.resultsExhausted);
        return answer.resultsExhausted;
    }

    @Override
    public boolean stopOnUnsolvablePartition() {
        return false;
    }
}
