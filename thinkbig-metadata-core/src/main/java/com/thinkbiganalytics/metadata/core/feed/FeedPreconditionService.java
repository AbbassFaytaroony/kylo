/**
 * 
 */
package com.thinkbiganalytics.metadata.core.feed;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

import javax.inject.Inject;

import com.thinkbiganalytics.metadata.api.dataset.Dataset;
import com.thinkbiganalytics.metadata.api.event.DataChangeEvent;
import com.thinkbiganalytics.metadata.api.event.DataChangeEventListener;
import com.thinkbiganalytics.metadata.api.feed.Feed;
import com.thinkbiganalytics.metadata.api.feed.FeedPrecondition;
import com.thinkbiganalytics.metadata.api.feed.FeedProvider;
import com.thinkbiganalytics.metadata.api.feed.precond.DependentFeedMetric;
import com.thinkbiganalytics.metadata.api.op.ChangeSet;
import com.thinkbiganalytics.metadata.api.op.ChangedContent;
import com.thinkbiganalytics.metadata.api.op.DataOperationsProvider;
import com.thinkbiganalytics.metadata.sla.api.AssessmentResult;
import com.thinkbiganalytics.metadata.sla.api.Metric;
import com.thinkbiganalytics.metadata.sla.api.MetricAssessment;
import com.thinkbiganalytics.metadata.sla.api.ObligationAssessment;
import com.thinkbiganalytics.metadata.sla.api.ServiceLevelAgreement;
import com.thinkbiganalytics.metadata.sla.api.ServiceLevelAssessment;
import com.thinkbiganalytics.metadata.sla.spi.ServiceLevelAssessor;

/**
 *
 * @author Sean Felten
 */
public class FeedPreconditionService {

    @Inject
    private ServiceLevelAssessor assessor;
    
    @Inject
    private FeedProvider feedProvider;
    
    @Inject
    private DataOperationsProvider operationsProvider;
    
    private Map<Feed.ID, Set<PreconditionListener>> feedListeners = new ConcurrentHashMap<>();
    private Set<PreconditionListener> generalListeners = new ConcurrentSkipListSet<>();
    
    public void addListener(Feed.ID id, PreconditionListener listener) {
        Set<PreconditionListener> set = this.feedListeners.get(id);
        if (set == null) {
            set = new HashSet<>();
            this.feedListeners.put(id, set);
        }
        set.add(listener);
    }
    
    public void addListener(PreconditionListener listener) {
        this.generalListeners.add(listener);
    }
    
    public void listenFeeds(Set<Metric> metrics) {
        for (Metric metric : metrics) {
            if (metric instanceof DependentFeedMetric) {
                listenFeed(((DependentFeedMetric) metric).getFeedName());
            }
        }
    }
    
    private void listenFeed(String feedName) {
        this.operationsProvider.addListener(createDataChangeListener(feedName));
    }

    private DataChangeEventListener<Dataset, ChangedContent> createDataChangeListener(final String feedName) {
        return new DataChangeEventListener<Dataset, ChangedContent>() {
            @Override
            public void handleEvent(DataChangeEvent<Dataset, ChangedContent> event) {
                for (Entry<Feed.ID, Set<PreconditionListener>> feedEntry : feedListeners.entrySet()) {
                    Feed feed = feedProvider.getFeed(feedEntry.getKey());
                    
                    if (feed != null) {
                        ServiceLevelAgreement sla = asAgreement(feed.getPrecondition());
                        List<ChangeSet<Dataset, ChangedContent>> changes = checkPrecondition(sla);
                        
                        if (changes != null) {
                            for (PreconditionListener listener : feedEntry.getValue()) {
                                PreconditionEvent preEv = new PreconditionEventImpl(feed, changes);
                                listener.triggered(preEv);
                            }
                            
                            for (PreconditionListener listener : generalListeners) {
                                PreconditionEvent preEv = new PreconditionEventImpl(feed, changes);
                                listener.triggered(preEv);
                            }
                        }
                    }
                }
            }
        };
    }

    protected ServiceLevelAgreement asAgreement(FeedPrecondition precondition) {
        // TODO Not the best...
        return ((BaseFeed.FeedPreconditionImpl) precondition).getAgreement();
    }

    private List<ChangeSet<Dataset, ChangedContent>> checkPrecondition(ServiceLevelAgreement sla) {
        ServiceLevelAssessment assmt = this.assessor.assess(sla);
        
        if (assmt.getResult() != AssessmentResult.FAILURE) {
            return collectResults(assmt);
        } else {
            return null;
        }
    }

    private List<ChangeSet<Dataset, ChangedContent>> collectResults(ServiceLevelAssessment assmt) {
        List<ChangeSet<Dataset, ChangedContent>> result = new ArrayList<>();
        
        for (ObligationAssessment obAssmt : assmt.getObligationAssessments()) {
            for (MetricAssessment<ArrayList<ChangeSet<Dataset, ChangedContent>>> mAssmt 
                    : obAssmt.<ArrayList<ChangeSet<Dataset, ChangedContent>>>getMetricAssessments()) {
                result.addAll(mAssmt.getData());
            }
        }
        
        return result;
    }
    
    private static class PreconditionEventImpl implements PreconditionEvent {
        
        private Feed feed;
        private List<ChangeSet<Dataset, ChangedContent>> changes;

        public PreconditionEventImpl(Feed feed, List<ChangeSet<Dataset, ChangedContent>> changes) {
            this.feed = feed;
            this.changes = changes;
        }

        @Override
        public List<ChangeSet<Dataset, ChangedContent>> getChanges() {
            return this.changes;
        }
        
        @Override
        public Feed getFeed() {
            return this.feed;
        }
    }

}
