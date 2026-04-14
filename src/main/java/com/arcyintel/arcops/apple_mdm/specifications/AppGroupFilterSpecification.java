package com.arcyintel.arcops.apple_mdm.specifications;

import com.arcyintel.arcops.apple_mdm.domains.AppGroup;
import com.arcyintel.arcops.apple_mdm.domains.AppGroupItem;
import com.arcyintel.arcops.apple_mdm.domains.EnterpriseApp;
import com.arcyintel.arcops.apple_mdm.domains.ItunesAppMeta;
import jakarta.persistence.criteria.*;

public class AppGroupFilterSpecification extends AbstractFilterSpecification<AppGroup> {

    private static final String[] GLOBAL_SEARCH_FIELDS = {"name", "description"};

    public AppGroupFilterSpecification(java.util.Map<String, String> filters) {
        this(filters, false, DEFAULT_FUZZY_THRESHOLD);
    }

    public AppGroupFilterSpecification(java.util.Map<String, String> filters, boolean fuzzy, double fuzzyThreshold) {
        super(filters, fuzzy, fuzzyThreshold);
    }

    @Override
    protected String[] getGlobalSearchFields() {
        return GLOBAL_SEARCH_FIELDS;
    }

    @Override
    protected Predicate handlePlatformFilter(Root<AppGroup> root, CriteriaQuery<?> query, CriteriaBuilder cb, String value) {
        String platformValue = value.toLowerCase();

        // Subquery: VPP items with matching platform via ItunesAppMeta.supportedPlatforms
        Subquery<Long> vppSubquery = query.subquery(Long.class);
        Root<AppGroupItem> vppItemRoot = vppSubquery.from(AppGroupItem.class);
        Root<ItunesAppMeta> itunesRoot = vppSubquery.from(ItunesAppMeta.class);
        Join<ItunesAppMeta, String> itunesPlatformJoin = itunesRoot.join("supportedPlatforms", JoinType.INNER);

        vppSubquery.select(cb.count(vppItemRoot));
        vppSubquery.where(
                cb.and(
                        cb.equal(vppItemRoot.get("appGroup"), root),
                        cb.equal(vppItemRoot.get("appType"), AppGroup.AppType.VPP),
                        cb.equal(
                                cb.function("cast_to_text", String.class, itunesRoot.get("trackId")),
                                vppItemRoot.get("trackId")
                        ),
                        cb.like(cb.lower(itunesPlatformJoin), "%" + platformValue + "%")
                )
        );

        // Subquery: Enterprise items with matching platform via EnterpriseApp.supportedPlatforms
        Subquery<Long> enterpriseSubquery = query.subquery(Long.class);
        Root<AppGroupItem> entItemRoot = enterpriseSubquery.from(AppGroupItem.class);
        Root<EnterpriseApp> enterpriseRoot = enterpriseSubquery.from(EnterpriseApp.class);
        Join<EnterpriseApp, String> entPlatformJoin = enterpriseRoot.join("supportedPlatforms", JoinType.INNER);

        enterpriseSubquery.select(cb.count(entItemRoot));
        enterpriseSubquery.where(
                cb.and(
                        cb.equal(entItemRoot.get("appGroup"), root),
                        cb.equal(entItemRoot.get("appType"), AppGroup.AppType.ENTERPRISE),
                        cb.equal(enterpriseRoot.get("bundleId"), entItemRoot.get("bundleId")),
                        cb.like(cb.lower(entPlatformJoin), "%" + platformValue + "%")
                )
        );

        return cb.or(
                cb.greaterThan(vppSubquery, 0L),
                cb.greaterThan(enterpriseSubquery, 0L)
        );
    }
}
