package com.jivesoftware.os.miru.stream.plugins.fulltext;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.jivesoftware.os.miru.api.query.filter.MiruValue;
import java.util.Arrays;
import java.util.List;

import static com.jivesoftware.os.miru.api.wal.MiruWALClient.RoutingGroupType.activity;

/** @author jonathan */
public class FullTextAnswer {

    public static final FullTextAnswer EMPTY_RESULTS = new FullTextAnswer(ImmutableList.<ActivityScore>of(), true);

    public final List<ActivityScore> results;
    public final boolean resultsExhausted;

    @JsonCreator
    public FullTextAnswer(
        @JsonProperty("results") List<ActivityScore> results,
        @JsonProperty("resultsExhausted") boolean resultsExhausted) {
        this.results = results;
        this.resultsExhausted = resultsExhausted;
    }

    @Override
    public String toString() {
        return "FullTextAnswer{" +
            "results=" + results +
            ", resultsExhausted=" + resultsExhausted +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        FullTextAnswer that = (FullTextAnswer) o;

        if (resultsExhausted != that.resultsExhausted) {
            return false;
        }
        return !(results != null ? !results.equals(that.results) : that.results != null);

    }

    @Override
    public int hashCode() {
        int result = results != null ? results.hashCode() : 0;
        result = 31 * result + (resultsExhausted ? 1 : 0);
        return result;
    }

    public static class ActivityScore implements Comparable<ActivityScore> {

        public final MiruValue[][] values;
        public final long timestamp;
        public final float score;

        @JsonCreator
        public ActivityScore(
            @JsonProperty("values") MiruValue[][] values,
            @JsonProperty("timestamp") long timestamp,
            @JsonProperty("score") float score) {
            this.values = values;
            this.timestamp = timestamp;
            this.score = score;
        }

        @Override
        public int compareTo(ActivityScore o) {
            // higher scores first
            int c = -Float.compare(score, o.score);
            if (c != 0) {
                return c;
            }
            // higher timestamps first
            return -Long.compare(timestamp, o.timestamp);
        }

        @Override
        public String toString() {
            return "ActivityScore{" +
                "values=" + Arrays.deepToString(values) +
                ", timestamp=" + timestamp +
                ", score=" + score +
                '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            ActivityScore that = (ActivityScore) o;

            if (timestamp != that.timestamp) {
                return false;
            }
            if (Float.compare(that.score, score) != 0) {
                return false;
            }
            return Arrays.deepEquals(values, that.values);

        }

        @Override
        public int hashCode() {
            int result = Arrays.deepHashCode(values);
            result = 31 * result + (int) (timestamp ^ (timestamp >>> 32));
            result = 31 * result + (score != +0.0f ? Float.floatToIntBits(score) : 0);
            return result;
        }
    }
}
