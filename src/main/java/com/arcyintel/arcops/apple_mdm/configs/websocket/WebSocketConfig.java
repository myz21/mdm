package com.arcyintel.arcops.apple_mdm.configs.websocket;

import com.arcyintel.arcops.apple_mdm.services.screenshare.ScreenShareSignalingHandler;
import com.arcyintel.arcops.apple_mdm.services.terminal.RemoteTerminalHandler;
import com.arcyintel.arcops.apple_mdm.services.vnc.VncTunnelHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final ScreenShareSignalingHandler screenShareSignalingHandler;
    private final RemoteTerminalHandler remoteTerminalHandler;
    private final VncTunnelHandler vncTunnelHandler;

    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxBinaryMessageBufferSize(5 * 1024 * 1024);  // 5 MB — VNC binary frames
        container.setMaxTextMessageBufferSize(64 * 1024);           // 64 KB
        return container;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(screenShareSignalingHandler, "/screen-share/ws")
                .setAllowedOriginPatterns("*");
        registry.addHandler(remoteTerminalHandler, "/remote-terminal/ws")
                .setAllowedOriginPatterns("*");
        registry.addHandler(vncTunnelHandler, "/vnc-tunnel/ws")
                .setAllowedOriginPatterns("*");
    }
}
