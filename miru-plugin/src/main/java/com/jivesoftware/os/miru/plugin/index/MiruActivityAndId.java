package com.jivesoftware.os.miru.plugin.index;

/**
 *
 * @author jonathan.colt
 */
public class MiruActivityAndId<A> implements Comparable<MiruActivityAndId<A>> {

    public final A activity;
    public final int id;

    public MiruActivityAndId(A activity, int id) {
        this.activity = activity;
        this.id = id;
    }

    @Override
    public int compareTo(MiruActivityAndId<A> o) {
        return Integer.compare(id, o.id);
    }
}
