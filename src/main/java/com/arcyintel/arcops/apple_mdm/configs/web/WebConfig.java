package com.arcyintel.arcops.apple_mdm.configs.web;

import com.arcyintel.arcops.commons.web.ApiResponseWrappingAdvice;
import com.arcyintel.arcops.commons.web.GlobalExceptionHandler;
import com.arcyintel.arcops.commons.web.TraceIdFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Configuration
public class WebConfig {

    @Bean
    public FilterRegistrationBean<TraceIdFilter> traceIdFilter() {
        FilterRegistrationBean<TraceIdFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new TraceIdFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @RestControllerAdvice(basePackages = "com.arcyintel.arcops.apple_mdm.controllers")
    static class AppleMdmResponseAdvice extends ApiResponseWrappingAdvice {}

    @RestControllerAdvice(basePackages = "com.arcyintel.arcops.apple_mdm.controllers")
    static class AppleMdmExceptionHandler extends GlobalExceptionHandler {}
}
