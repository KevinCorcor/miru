package com.jivesoftware.os.miru.stream.plugins.filter;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jivesoftware.os.miru.plugin.solution.MiruAnswerMerger;
import com.jivesoftware.os.miru.plugin.solution.MiruSolutionLog;
import com.jivesoftware.os.miru.plugin.solution.MiruSolutionLogLevel;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author jonathan
 */
public class AggregateCountsAnswerMerger implements MiruAnswerMerger<AggregateCountsAnswer> {

    /**
     * Merges the last and current results, returning the merged result.
     *
     * @param last          the last merge result
     * @param currentAnswer the next result to merge
     * @return the merged result
     */
    @Override
    public AggregateCountsAnswer merge(Optional<AggregateCountsAnswer> last, AggregateCountsAnswer currentAnswer, MiruSolutionLog solutionLog) {
        if (!last.isPresent()) {
            return currentAnswer;
        }

        AggregateCountsAnswer lastAnswer = last.get();

        Map<String, AggregateCountsAnswerConstraint> mergedConstraints = Maps.newHashMapWithExpectedSize(currentAnswer.constraints.size());
        for (Map.Entry<String, AggregateCountsAnswerConstraint> entry : currentAnswer.constraints.entrySet()) {

            AggregateCountsAnswerConstraint currentConstraint = entry.getValue();
            AggregateCountsAnswerConstraint lastConstraint = lastAnswer.constraints.get(entry.getKey());
            if (lastConstraint == null) {
                mergedConstraints.put(entry.getKey(), currentConstraint);
            } else {

                Map<String, AggregateCount> carryOverCounts = new HashMap<>();
                for (AggregateCount aggregateCount : currentConstraint.results) {
                    carryOverCounts.put(aggregateCount.distinctValue, aggregateCount);
                }

                List<AggregateCount> mergedResults = Lists.newArrayListWithCapacity(lastConstraint.results.size() + currentConstraint.results.size());
                for (AggregateCount aggregateCount : lastConstraint.results) {
                    AggregateCount had = carryOverCounts.remove(aggregateCount.distinctValue);
                    if (had == null) {
                        mergedResults.add(aggregateCount);
                    } else {
                        mergedResults.add(new AggregateCount(aggregateCount.mostRecentActivity, aggregateCount.distinctValue, aggregateCount.count + had.count,
                            aggregateCount.unread || had.unread));
                    }
                }
                for (AggregateCount aggregateCount : currentConstraint.results) {
                    if (carryOverCounts.containsKey(aggregateCount.distinctValue) && aggregateCount.mostRecentActivity != null) {
                        mergedResults.add(aggregateCount);
                    }
                }

                AggregateCountsAnswerConstraint mergedAnswerConstraint = new AggregateCountsAnswerConstraint(
                    mergedResults,
                    currentConstraint.aggregateTerms,
                    currentConstraint.skippedDistincts,
                    currentConstraint.collectedDistincts);

                mergedConstraints.put(entry.getKey(), mergedAnswerConstraint);
            }

        }
        AggregateCountsAnswer mergedAnswer = new AggregateCountsAnswer(mergedConstraints, currentAnswer.resultsExhausted);

        logMergeResult(currentAnswer, lastAnswer, mergedAnswer, solutionLog);

        return mergedAnswer;
    }

    @Override
    public AggregateCountsAnswer done(Optional<AggregateCountsAnswer> last, AggregateCountsAnswer alternative, MiruSolutionLog solutionLog) {
        return last.or(alternative);
    }

    private void logMergeResult(AggregateCountsAnswer currentAnswer,
        AggregateCountsAnswer lastAnswer,
        AggregateCountsAnswer mergedAnswer,
        MiruSolutionLog solutionLog) {

        if (solutionLog.isLogLevelEnabled(MiruSolutionLogLevel.INFO)) {

            for (Map.Entry<String, AggregateCountsAnswerConstraint> entry : currentAnswer.constraints.entrySet()) {

                AggregateCountsAnswerConstraint currentConstraint = entry.getValue();
                AggregateCountsAnswerConstraint lastConstraint = lastAnswer.constraints.get(entry.getKey());
                AggregateCountsAnswerConstraint mergedConstraint = mergedAnswer.constraints.get(entry.getKey());

                if (lastConstraint != null) {

                    solutionLog.log(MiruSolutionLogLevel.INFO, " Merged: {}"
                            + "\n  From: terms={} results={} collected={} skipped={}"
                            + "\n  With: terms={} results={} collected={} skipped={}"
                            + "\n  To:   terms={} results={} collected={} skipped={}",
                        entry.getKey(),
                        lastConstraint.aggregateTerms.size(), lastConstraint.results.size(), lastConstraint.collectedDistincts, lastConstraint.skippedDistincts,
                        currentConstraint.aggregateTerms.size(), currentConstraint.results.size(), currentConstraint.collectedDistincts,
                        currentConstraint.skippedDistincts,
                        mergedConstraint.aggregateTerms.size(), mergedConstraint.results.size(), mergedConstraint.collectedDistincts,
                        mergedConstraint.skippedDistincts);
                }
            }
        }
    }
}
