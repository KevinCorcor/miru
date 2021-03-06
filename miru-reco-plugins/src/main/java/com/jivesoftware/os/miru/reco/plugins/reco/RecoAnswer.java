package com.jivesoftware.os.miru.reco.plugins.reco;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.jivesoftware.os.miru.api.query.filter.MiruValue;
import java.util.List;

/**
 *
 */
public class RecoAnswer {

    public static final RecoAnswer EMPTY_RESULTS = new RecoAnswer(ImmutableList.<Recommendation>of(), 0, true);

    public final List<Recommendation> results;
    public final int partitionsVisited;
    public final boolean resultsExhausted;

    @JsonCreator
    public RecoAnswer(@JsonProperty("results") List<Recommendation> results,
        @JsonProperty("partitionsVisited") int partitionsVisited,
        @JsonProperty("resultsExhausted") boolean resultsExhausted) {
        this.results = results;
        this.partitionsVisited = partitionsVisited;
        this.resultsExhausted = resultsExhausted;
    }

    @Override
    public String toString() {
        return "RecoAnswer{" +
            "results=" + results +
            ", partitionsVisited=" + partitionsVisited +
            ", resultsExhausted=" + resultsExhausted +
            '}';
    }

    public static class Recommendation implements Comparable<Recommendation> {

        public final MiruValue distinctValue;
        public final float rank;

        public Recommendation(MiruValue distinctValue, float rank) {
            this.distinctValue = distinctValue;
            this.rank = rank;
        }

        @JsonCreator
        public static Recommendation fromJson(
            @JsonProperty("distinctValue") MiruValue distinctValue,
            @JsonProperty("rank") float rank)
            throws Exception {
            return new Recommendation(distinctValue, rank);
        }

        @Override
        public int compareTo(Recommendation o) {
            // reversed for descending order
            return Double.compare(o.rank, rank);
        }

        @Override
        public String toString() {
            return "Recommendation{"
                + ", distinctValue=" + distinctValue
                + ", rank=" + rank
                + '}';
        }
    }

}
