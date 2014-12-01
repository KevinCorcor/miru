package com.jivesoftware.os.miru.reco.plugins.distincts;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.jivesoftware.os.miru.api.base.MiruTermId;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 *
 */
public class DistinctsAnswer implements Serializable {

    public static final DistinctsAnswer EMPTY_RESULTS = new DistinctsAnswer(Collections.<MiruTermId>emptyList(), 0, true);

    public final List<MiruTermId> results;
    public final int collectedDistincts;
    public final boolean resultsExhausted;

    public DistinctsAnswer(
        List<MiruTermId> results,
        int collectedDistincts,
        boolean resultsExhausted) {
        this.results = results;
        this.collectedDistincts = collectedDistincts;
        this.resultsExhausted = resultsExhausted;
    }

    @JsonCreator
    public static DistinctsAnswer fromJson(
        @JsonProperty("results") List<MiruTermId> results,
        @JsonProperty("collectedDistincts") int collectedDistincts,
        @JsonProperty("resultsExhausted") boolean resultsExhausted) {
        return new DistinctsAnswer(new ArrayList<>(results), collectedDistincts, resultsExhausted);
    }

    @Override
    public String toString() {
        return "DistinctsAnswer{"
            + "results=" + results
            + ", collectedDistincts=" + collectedDistincts
            + ", resultsExhausted=" + resultsExhausted
            + '}';
    }

}
