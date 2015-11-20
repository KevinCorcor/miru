/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.jivesoftware.os.miru.plugin.index;

import com.jivesoftware.os.miru.plugin.solution.MiruTimeRange;
import java.util.List;

/**
 * @author jonathan
 */
public interface MiruTimeIndex {

    boolean[] contains(List<Long> timestamps, byte[] primitiveBuffer) throws Exception;

    boolean intersects(MiruTimeRange timeRange);

    /**
     * Returns the actual index of the given timestamp if it appears in the index, or else where it would have been.
     *
     * @param timestamp the activity timestamp
     * @return the actual index or nearest insertion point
     */
    int getClosestId(long timestamp, byte[] primitiveBuffer);

    int getExactId(long timestamp, byte[] primitiveBuffer) throws Exception;

    long getLargestTimestamp();

    long getSmallestTimestamp();

    long getTimestamp(int id, byte[] primitiveBuffer);

    /**
     * Returns the smallest id satisfying the condition that {@link #getTimestamp(int)} is greater than the requested timestamp (exclusive lower bound).
     *
     * @param timestamp the timestamp serving as the exclusive lower bound
     * @return the smallest id exclusive of the requested timestamp
     */
    int smallestExclusiveTimestampIndex(long timestamp, byte[] primitiveBuffer);

    /**
     * Returns the largest id satisfying the condition that {@link #getTimestamp(int)} is less than or equal to the requested timestamp (inclusive upper bound).
     *
     * @param timestamp the timestamp serving as the inclusive upper bound
     * @return the largest id inclusive of the requested timestamp
     */
    int largestInclusiveTimestampIndex(long timestamp, byte[] primitiveBuffer);

    int lastId();

    int[] nextId(byte[] primitiveBuffer, long... timestamps) throws Exception;

    void close();

}
