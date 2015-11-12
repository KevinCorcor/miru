package com.jivesoftware.os.miru.stream.plugins.filter;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;
import com.jivesoftware.os.miru.api.base.MiruStreamId;
import com.jivesoftware.os.miru.api.query.filter.MiruFilter;
import com.jivesoftware.os.miru.plugin.solution.MiruTimeRange;
import java.util.Map;

/**
 *
 */
public class AggregateCountsQuery {

    public final MiruStreamId streamId;
    public final MiruTimeRange answerTimeRange;
    public final MiruTimeRange countTimeRange;
    public final MiruFilter streamFilter;
    public final Map<String, AggregateCountsQueryConstraint> constraints;
    

    public AggregateCountsQuery(
        @JsonProperty("streamId") MiruStreamId streamId,
        @JsonProperty("answerTimeRange") MiruTimeRange answerTimeRange,
        @JsonProperty("countTimeRange") MiruTimeRange countTimeRange,
        @JsonProperty("streamFilter") MiruFilter streamFilter,
        @JsonProperty("constraints") Map<String, AggregateCountsQueryConstraint> constraints) {
        this.streamId = Preconditions.checkNotNull(streamId);
        this.answerTimeRange = Preconditions.checkNotNull(answerTimeRange);
        this.countTimeRange = Preconditions.checkNotNull(countTimeRange);
        this.streamFilter = Preconditions.checkNotNull(streamFilter);
        this.constraints = Preconditions.checkNotNull(constraints);
        
    }

    @Override
    public String toString() {
        return "AggregateCountsQuery{"
            + "streamId=" + streamId
            + ", answerTimeRange=" + answerTimeRange
            + ", countTimeRange=" + countTimeRange
            + ", streamFilter=" + streamFilter
            + ", constraints=" + constraints
            
            + '}';
    }
}
