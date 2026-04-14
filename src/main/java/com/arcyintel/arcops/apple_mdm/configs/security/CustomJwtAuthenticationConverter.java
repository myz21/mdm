package com.arcyintel.arcops.apple_mdm.configs.security;


import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;
import java.util.Collections;

public class CustomJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {


    @Override
    public AbstractAuthenticationToken convert(Jwt source) {

        if (source.getClaims().get("grant_type").equals(AuthorizationGrantType.CLIENT_CREDENTIALS.getValue())) {
            return new JwtAuthenticationToken(source, AuthorityUtils.NO_AUTHORITIES);
        }

        Collection<String> authorities = source.getClaimAsStringList("authorities");
        if (authorities == null) {
            authorities = Collections.emptyList();
        }

        return new JwtAuthenticationToken(source, AuthorityUtils.createAuthorityList(authorities.toArray(new String[0])));
    }
}