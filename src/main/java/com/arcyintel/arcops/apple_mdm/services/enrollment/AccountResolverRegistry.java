package com.arcyintel.arcops.apple_mdm.services.enrollment;

import com.arcyintel.arcops.apple_mdm.domains.AppleAccount;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Registry for account resolvers.
 *
 * Manages multiple AccountResolver implementations and provides
 * methods to select the appropriate resolver based on context.
 */
@Service
@RequiredArgsConstructor
public class AccountResolverRegistry {

    private static final Logger logger = LoggerFactory.getLogger(AccountResolverRegistry.class);

    private final List<AccountResolver> resolvers;

    /**
     * Get all registered resolvers as a map by type.
     */
    public Map<String, AccountResolver> getResolvers() {
        return resolvers.stream()
                .collect(Collectors.toMap(AccountResolver::getType, Function.identity()));
    }

    /**
     * Get a specific resolver by type.
     */
    public Optional<AccountResolver> getResolver(String type) {
        return resolvers.stream()
                .filter(r -> r.getType().equals(type))
                .findFirst();
    }

    /**
     * Resolve an account using the specified resolver type.
     */
    public Optional<AppleAccount> resolve(String resolverType, String identifier) {
        Optional<AccountResolver> resolver = getResolver(resolverType);
        if (resolver.isEmpty()) {
            logger.warn("No resolver found for type: {}", resolverType);
            return Optional.empty();
        }
        return resolver.get().resolve(identifier);
    }

    /**
     * Resolve an account using context to determine the resolver.
     */
    public Optional<AppleAccount> resolve(AccountResolutionContext context) {
        if (context == null) {
            return Optional.empty();
        }

        // If a specific resolver type is requested, use that
        if (context.getResolverType() != null) {
            Optional<AccountResolver> resolver = getResolver(context.getResolverType());
            if (resolver.isPresent()) {
                return resolver.get().resolve(context.getIdentifier(), context);
            }
        }

        // Try resolvers based on identity source
        String resolverType = mapIdentitySourceToResolver(context.getIdentitySource());
        if (resolverType != null) {
            Optional<AccountResolver> resolver = getResolver(resolverType);
            if (resolver.isPresent()) {
                Optional<AppleAccount> result = resolver.get().resolve(context.getIdentifier(), context);
                if (result.isPresent()) {
                    return result;
                }
            }
        }

        // Fall back to trying all resolvers
        for (AccountResolver resolver : resolvers) {
            Optional<AppleAccount> result = resolver.resolve(context.getIdentifier(), context);
            if (result.isPresent()) {
                logger.info("Account resolved by fallback resolver: {}", resolver.getType());
                return result;
            }
        }

        return Optional.empty();
    }

    /**
     * Try to create or update an account using the appropriate resolver.
     */
    public Optional<AppleAccount> createOrUpdate(AccountResolutionContext context) {
        if (context == null) {
            return Optional.empty();
        }

        String resolverType = context.getResolverType();
        if (resolverType == null) {
            resolverType = mapIdentitySourceToResolver(context.getIdentitySource());
        }

        if (resolverType != null) {
            Optional<AccountResolver> resolver = getResolver(resolverType);
            if (resolver.isPresent() && resolver.get().supportsAutoCreation()) {
                return resolver.get().createOrUpdate(context);
            }
        }

        // Try first resolver that supports auto-creation
        for (AccountResolver resolver : resolvers) {
            if (resolver.supportsAutoCreation()) {
                return resolver.createOrUpdate(context);
            }
        }

        return Optional.empty();
    }

    /**
     * Maps identity source to resolver type.
     */
    private String mapIdentitySourceToResolver(AccountResolutionContext.IdentitySource source) {
        if (source == null) {
            return null;
        }
        return switch (source) {
            case MANAGED_APPLE_ID -> "MANAGED_APPLE_ID";
            case FEDERATED_IDP -> "FEDERATED_AUTH"; // Future implementation
            case WEB_AUTH -> "WEB_AUTH";
            case ENROLLMENT_TOKEN -> "ENROLLMENT_TOKEN";
            default -> null;
        };
    }
}
