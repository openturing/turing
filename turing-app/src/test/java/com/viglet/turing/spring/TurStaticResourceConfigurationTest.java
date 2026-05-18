package com.viglet.turing.spring;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.CorsRegistration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceChainRegistration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;

@ExtendWith(MockitoExtension.class)
class TurStaticResourceConfigurationTest {

    private TurStaticResourceConfiguration configuration;

    @Mock
    private CorsRegistry corsRegistry;
    @Mock
    private CorsRegistration corsRegistration;
    @Mock
    private AsyncSupportConfigurer asyncSupportConfigurer;
    @Mock
    private ViewControllerRegistry viewControllerRegistry;
    @Mock
    private ViewControllerRegistration viewControllerRegistration;
    @Mock
    private ResourceHandlerRegistry resourceHandlerRegistry;
    @Mock
    private ResourceHandlerRegistration resourceHandlerRegistration;
    @Mock
    private ResourceChainRegistration resourceChainRegistration;

    @BeforeEach
    void setUp() {
        configuration = new TurStaticResourceConfiguration();
    }

    @Test
    void shouldConfigureCorsUsingConfiguredOrigins() {
        ReflectionTestUtils.setField(configuration, "allowedOrigins", "http://one.local,http://two.local");
        when(corsRegistry.addMapping("/api/**")).thenReturn(corsRegistration);
        when(corsRegistration.allowedOrigins(any(String[].class))).thenReturn(corsRegistration);
        when(corsRegistration.allowedMethods(any(String[].class))).thenReturn(corsRegistration);
        when(corsRegistration.allowedHeaders(any(String[].class))).thenReturn(corsRegistration);
        when(corsRegistration.allowCredentials(true)).thenReturn(corsRegistration);
        when(corsRegistration.maxAge(3600)).thenReturn(corsRegistration);

        configuration.addCorsMappings(corsRegistry);

        verify(corsRegistry).addMapping("/api/**");
        verify(corsRegistration).allowedOrigins("http://one.local", "http://two.local");
        verify(corsRegistration).allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH");
        verify(corsRegistration).allowedHeaders("*");
        verify(corsRegistration).allowCredentials(true);
        verify(corsRegistration).maxAge(3600);
    }

    @Test
    void shouldConfigureDefaultAsyncTimeoutAsInfinite() {
        configuration.configureAsyncSupport(asyncSupportConfigurer);

        verify(asyncSupportConfigurer).setDefaultTimeout(-1);
    }

    @Test
    void shouldRegisterAllFrontendViewControllers() {
        when(viewControllerRegistry.addViewController(anyString())).thenReturn(viewControllerRegistration);

        configuration.addViewControllers(viewControllerRegistry);

        verify(viewControllerRegistry).addViewController("/login");
        verify(viewControllerRegistry).addViewController("/login/");
        verify(viewControllerRegistry).addViewController("/admin");
        verify(viewControllerRegistry).addViewController("/admin/");
        verify(viewControllerRegistry).addViewController("/setup");
        verify(viewControllerRegistry).addViewController("/setup/");
        verify(viewControllerRegistry).addViewController("/bento");
        verify(viewControllerRegistry).addViewController("/bento/");
        verify(viewControllerRegistration, times(8)).setViewName(anyString());
    }

    @Test
    void shouldRegisterResourceHandlersForAngularAndReactContexts() {
        when(resourceHandlerRegistry.addResourceHandler(anyString())).thenReturn(resourceHandlerRegistration);
        when(resourceHandlerRegistration.addResourceLocations(anyString())).thenReturn(resourceHandlerRegistration);
        when(resourceHandlerRegistration.resourceChain(true)).thenReturn(resourceChainRegistration);
        when(resourceChainRegistration.addResolver(any())).thenReturn(resourceChainRegistration);

        configuration.addResourceHandlers(resourceHandlerRegistry);

        verify(resourceHandlerRegistry).addResourceHandler("/login/**");
        verify(resourceHandlerRegistry).addResourceHandler("/admin/**");
        verify(resourceHandlerRegistry).addResourceHandler("/setup/**");
        verify(resourceHandlerRegistry).addResourceHandler("/bento/**");
        verify(resourceHandlerRegistration, times(4)).resourceChain(true);
    }
}
