package org.skywalking.apm.agent.core.remote;

import io.grpc.NameResolver;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.internal.DnsNameResolverProvider;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.testing.GrpcServerRule;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;
import org.skywalking.apm.agent.core.conf.Config;
import org.skywalking.apm.agent.core.conf.RemoteDownstreamConfig;
import org.skywalking.apm.agent.core.test.tools.AgentServiceRule;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({GRPCChannelManager.class, NettyChannelBuilder.class})
public class GRPCChannelManagerTest {

    @Rule
    private GrpcServerRule grpcServerRule = new GrpcServerRule().directExecutor();

    @Spy
    private GRPCChannelManager grpcChannelManager = new GRPCChannelManager();

    @Mock
    private NettyChannelBuilder mock;

    @Spy
    private MockGRPCChannelListener listener = new MockGRPCChannelListener();

    @Before
    public void setUp() throws Throwable {
        List<String> grpcServers = new ArrayList<String>();
        grpcServers.add("127.0.0.1:2181");
        RemoteDownstreamConfig.Collector.GRPC_SERVERS = grpcServers;
        Config.Collector.GRPC_CHANNEL_CHECK_INTERVAL = 1;

        mockStatic(NettyChannelBuilder.class);
        when(NettyChannelBuilder.forAddress(anyString(), anyInt())).thenReturn(mock);
        when(mock.nameResolverFactory(any(NameResolver.Factory.class))).thenReturn(mock);
        when(mock.maxInboundMessageSize(anyInt())).thenReturn(mock);
        when(mock.usePlaintext(true)).thenReturn(mock);
        when(mock.build()).thenReturn(grpcServerRule.getChannel());

        grpcChannelManager.addChannelListener(listener);
    }

    @Test
    public void changeStatusToConnectedWithReportError() throws Throwable {
        grpcChannelManager.reportError(new StatusRuntimeException(Status.ABORTED));
        grpcChannelManager.run();

        verify(listener, times(1)).statusChanged(GRPCChannelStatus.CONNECTED);
        assertThat(listener.status, is(GRPCChannelStatus.CONNECTED));
    }

    @Test
    public void changeStatusToDisConnectedWithReportError() throws Throwable {
        doThrow(new RuntimeException()).when(mock).nameResolverFactory(any(NameResolver.Factory.class));
        grpcChannelManager.run();

        verify(listener, times(1)).statusChanged(GRPCChannelStatus.DISCONNECT);
        assertThat(listener.status, is(GRPCChannelStatus.DISCONNECT));
    }

    @Test
    public void reportErrorWithoutChangeStatus() throws Throwable {
        grpcChannelManager.run();
        grpcChannelManager.reportError(new RuntimeException());
        grpcChannelManager.run();

        verify(listener, times(1)).statusChanged(GRPCChannelStatus.CONNECTED);
        assertThat(listener.status, is(GRPCChannelStatus.CONNECTED));
    }

    private class MockGRPCChannelListener implements GRPCChannelListener {
        private GRPCChannelStatus status;

        @Override
        public void statusChanged(GRPCChannelStatus status) {
            this.status = status;
        }
    }

}
