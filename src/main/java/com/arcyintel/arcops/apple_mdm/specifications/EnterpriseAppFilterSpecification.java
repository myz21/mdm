package com.arcyintel.arcops.apple_mdm.specifications;

import com.arcyintel.arcops.apple_mdm.domains.EnterpriseApp;
import jakarta.persistence.criteria.*;

public class EnterpriseAppFilterSpecification extends AbstractFilterSpecification<EnterpriseApp> {

    private static final String SUPPORTED_PLATFORMS_FIELD = "supportedPlatforms";
    private static final String[] GLOBAL_SEARCH_FIELDS = {"displayName", "bundleId", "platform"};

    public EnterpriseAppFilterSpecification(java.util.Map<String, String> filters) {
        this(filters, false, DEFAULT_FUZZY_THRESHOLD);
    }

    public EnterpriseAppFilterSpecification(java.util.Map<String, String> filters, boolean fuzzy, double fuzzyThreshold) {
        super(filters, fuzzy, fuzzyThreshold);
    }

    @Override
    protected String[] getGlobalSearchFields() {
        return GLOBAL_SEARCH_FIELDS;
    }

    @Override
    protected Predicate handlePlatformFilter(Root<EnterpriseApp> root, CriteriaQuery<?> query, CriteriaBuilder cb, String value) {
        Join<EnterpriseApp, String> platformJoin = root.join(SUPPORTED_PLATFORMS_FIELD, JoinType.INNER);
        return buildFuzzyOrLikePredicate(cb, platformJoin, value);
    }
}
