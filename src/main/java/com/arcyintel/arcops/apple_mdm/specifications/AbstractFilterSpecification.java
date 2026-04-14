package com.arcyintel.arcops.apple_mdm.specifications;

import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class AbstractFilterSpecification<T> implements Specification<T> {

    protected static final String GLOBAL_SEARCH_KEY = "q";
    protected static final String PLATFORM_FILTER_KEY = "platform";
    protected static final double DEFAULT_FUZZY_THRESHOLD = 0.3;

    protected final Map<String, String> filters;
    protected final boolean fuzzy;
    protected final double fuzzyThreshold;

    protected AbstractFilterSpecification(Map<String, String> filters, boolean fuzzy, double fuzzyThreshold) {
        this.filters = filters;
        this.fuzzy = fuzzy;
        this.fuzzyThreshold = fuzzyThreshold;
    }

    protected abstract String[] getGlobalSearchFields();

    protected abstract Predicate handlePlatformFilter(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb, String value);

    @Override
    public Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        List<Predicate> predicates = new ArrayList<>();

        if (filters == null || filters.isEmpty()) {
            return cb.conjunction();
        }

        for (Map.Entry<String, String> entry : filters.entrySet()) {
            String fieldName = entry.getKey();
            String value = entry.getValue();
            if (value == null || value.isBlank()) continue;

            if (GLOBAL_SEARCH_KEY.equalsIgnoreCase(fieldName)) {
                Predicate globalPredicate = buildGlobalSearchPredicate(root, cb, value);
                if (globalPredicate != null) {
                    predicates.add(globalPredicate);
                }
                continue;
            }

            if (PLATFORM_FILTER_KEY.equalsIgnoreCase(fieldName)) {
                Predicate platformPredicate = handlePlatformFilter(root, query, cb, value);
                if (platformPredicate != null) {
                    predicates.add(platformPredicate);
                    query.distinct(true);
                }
            } else {
                Predicate fieldPredicate = buildFieldPredicate(root, cb, fieldName, value);
                if (fieldPredicate != null) {
                    predicates.add(fieldPredicate);
                }
            }
        }

        return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
    }

    private Predicate buildGlobalSearchPredicate(Root<T> root, CriteriaBuilder cb, String value) {
        List<Predicate> orPredicates = new ArrayList<>();
        for (String searchField : getGlobalSearchFields()) {
            try {
                Expression<String> asText = cb.function("cast_to_text", String.class, root.get(searchField));
                orPredicates.add(buildFuzzyOrLikePredicate(cb, asText, value));
            } catch (IllegalArgumentException e) {
                // field doesn't exist, skip
            }
        }
        if (!orPredicates.isEmpty()) {
            return cb.or(orPredicates.toArray(new Predicate[0]));
        }
        return null;
    }

    private Predicate buildFieldPredicate(Root<T> root, CriteriaBuilder cb, String fieldName, String value) {
        try {
            Expression<String> asText = cb.function("cast_to_text", String.class, root.get(fieldName));
            return buildFuzzyOrLikePredicate(cb, asText, value);
        } catch (IllegalArgumentException e) {
            // Field doesn't exist, skip it
            return null;
        }
    }

    protected Predicate buildFuzzyOrLikePredicate(CriteriaBuilder cb, Expression<String> expression, String value) {
        if (fuzzy) {
            Expression<Double> similarity = cb.function(
                    "trgm_similarity", Double.class,
                    cb.lower(expression), cb.literal(value.toLowerCase()));
            return cb.greaterThanOrEqualTo(similarity, fuzzyThreshold);
        } else {
            return cb.like(cb.lower(expression), "%" + value.toLowerCase() + "%");
        }
    }
}
