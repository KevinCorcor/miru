package com.jivesoftware.os.miru.plugin.query;

import com.google.common.collect.Lists;
import com.jivesoftware.os.miru.api.field.MiruFieldType;
import com.jivesoftware.os.miru.api.query.filter.MiruFieldFilter;
import com.jivesoftware.os.miru.api.query.filter.MiruFilter;
import com.jivesoftware.os.miru.api.query.filter.MiruFilterOperation;
import com.jivesoftware.os.miru.api.query.filter.MiruValue;
import java.util.Collections;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;

/**
 * Due to its reliance on the Lucene {@link QueryParser}, this class is NOT thread-safe.
 */
public class LuceneBackedQueryParser implements MiruQueryParser {

    private final String defaultField;
    private final TermAnalyzers termAnalyzers = new TermAnalyzers();

    public LuceneBackedQueryParser(String defaultField) {
        this.defaultField = defaultField;
    }

    @Override
    public MiruFilter parse(String locale, String queryString) throws Exception {
        Analyzer analyzer = termAnalyzers.findAnalyzer(locale);
        QueryParser parser = new QueryParser(defaultField, analyzer);
        return makeFilter(parser.parse(queryString));
    }

    private MiruFilter makeFilter(Query query) {
        if (query instanceof BooleanQuery) {
            BooleanQuery bq = (BooleanQuery) query;
            List<MiruFilter> musts = Lists.newArrayList();
            List<MiruFilter> shoulds = Lists.newArrayList();
            List<MiruFilter> mustNots = Lists.newArrayList();
            for (BooleanClause clause : bq) {
                Query subQuery = clause.getQuery();
                MiruFilter subFilter = makeFilter(subQuery);
                if (clause.getOccur() == BooleanClause.Occur.MUST) {
                    musts.add(subFilter);
                } else if (clause.getOccur() == BooleanClause.Occur.SHOULD) {
                    shoulds.add(subFilter);
                } else if (clause.getOccur() == BooleanClause.Occur.MUST_NOT) {
                    mustNots.add(subFilter);
                }
            }
            return wrap(musts, shoulds, mustNots);
        } else if (query instanceof TermQuery) {
            TermQuery tq = (TermQuery) query;
            Term term = tq.getTerm();
            return new MiruFilter(MiruFilterOperation.and, false,
                Collections.singletonList(MiruFieldFilter.ofTerms(MiruFieldType.primary, term.field(), term.text())),
                Collections.<MiruFilter>emptyList());
        } else if (query instanceof PrefixQuery) {
            PrefixQuery pq = (PrefixQuery) query;
            Term term = pq.getPrefix();
            return new MiruFilter(MiruFilterOperation.and, false,
                Collections.singletonList(MiruFieldFilter.ofValues(MiruFieldType.primary, term.field(), new MiruValue(term.text(), "*"))),
                Collections.<MiruFilter>emptyList());
        } else {
            throw new IllegalArgumentException("Unsupported query type: " + query.getClass());
        }
    }

    private MiruFilter wrap(List<MiruFilter> musts, List<MiruFilter> shoulds, List<MiruFilter> mustNots) {
        if (!musts.isEmpty()) {
            if (!mustNots.isEmpty()) {
                List<MiruFilter> filters = Lists.newArrayList();
                filters.add(wrap(musts, shoulds, Collections.<MiruFilter>emptyList()));
                filters.addAll(mustNots);
                return new MiruFilter(MiruFilterOperation.pButNotQ, false,
                    Collections.<MiruFieldFilter>emptyList(),
                    filters);
            } else if (!shoulds.isEmpty()) {
                musts.add(wrap(Collections.<MiruFilter>emptyList(), shoulds, Collections.<MiruFilter>emptyList()));
                return new MiruFilter(MiruFilterOperation.and, false,
                    Collections.<MiruFieldFilter>emptyList(),
                    musts);
            } else {
                return new MiruFilter(MiruFilterOperation.and, false, Collections.<MiruFieldFilter>emptyList(), musts);
            }
        } else if (!shoulds.isEmpty()) {
            if (!mustNots.isEmpty()) {
                List<MiruFilter> filters = Lists.newArrayList();
                filters.add(wrap(musts, shoulds, Collections.<MiruFilter>emptyList()));
                filters.addAll(mustNots);
                return new MiruFilter(MiruFilterOperation.pButNotQ, false,
                    Collections.<MiruFieldFilter>emptyList(),
                    filters);
            } else {
                return new MiruFilter(MiruFilterOperation.or, false, Collections.<MiruFieldFilter>emptyList(), shoulds);
            }
        } else if (!mustNots.isEmpty()) {
            return new MiruFilter(MiruFilterOperation.pButNotQ, true, Collections.<MiruFieldFilter>emptyList(), mustNots);
        } else {
            throw new IllegalArgumentException("Nothing to filter");
        }
    }

}
