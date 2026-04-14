package com.arcyintel.arcops.apple_mdm.specifications;

import com.arcyintel.arcops.apple_mdm.domains.ItunesAppMeta;
import jakarta.persistence.criteria.*;

public class ItunesAppFilterSpecification extends AbstractFilterSpecification<ItunesAppMeta> {

    private static final String SUPPORTED_PLATFORMS_FIELD = "supportedPlatforms";
    private static final String[] GLOBAL_SEARCH_FIELDS = {"trackName", "bundleId", "sellerName", "primaryGenreName"};

    public ItunesAppFilterSpecification(java.util.Map<String, String> filters) {
        this(filters, false, DEFAULT_FUZZY_THRESHOLD);
    }

    public ItunesAppFilterSpecification(java.util.Map<String, String> filters, boolean fuzzy, double fuzzyThreshold) {
        super(filters, fuzzy, fuzzyThreshold);
    }

    @Override
    protected String[] getGlobalSearchFields() {
        return GLOBAL_SEARCH_FIELDS;
    }

    @Override
    protected Predicate handlePlatformFilter(Root<ItunesAppMeta> root, CriteriaQuery<?> query, CriteriaBuilder cb, String value) {
        Join<ItunesAppMeta, String> platformJoin = root.join(SUPPORTED_PLATFORMS_FIELD, JoinType.INNER);
        return buildFuzzyOrLikePredicate(cb, platformJoin, value);
    }
}
