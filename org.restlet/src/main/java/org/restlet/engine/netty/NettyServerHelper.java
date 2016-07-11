/**
 * Copyright 2005-2014 Restlet
 * 
 * The contents of this file are subject to the terms of one of the following
 * open source licenses: Apache 2.0 or or EPL 1.0 (the "Licenses"). You can
 * select the license that you prefer but you may not use this file except in
 * compliance with one of these Licenses.
 * 
 * You can obtain a copy of the Apache 2.0 license at
 * http://www.opensource.org/licenses/apache-2.0
 * 
 * You can obtain a copy of the EPL 1.0 license at
 * http://www.opensource.org/licenses/eclipse-1.0
 * 
 * See the Licenses for the specific language governing permissions and
 * limitations under the Licenses.
 * 
 * Alternatively, you can obtain a royalty free commercial license with less
 * limitations, transferable or non-transferable, directly at
 * http://restlet.com/products/restlet-framework
 * 
 * Restlet is a registered trademark of Restlet S.A.S.
 */

package org.restlet.engine.netty;

import java.net.InetSocketAddress;

import org.reactivestreams.Processor;
import org.restlet.Server;

import com.typesafe.netty.HandlerPublisher;
import com.typesafe.netty.HandlerSubscriber;
import com.typesafe.netty.http.HttpStreamsServerHandler;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseEncoder;

/**
 * 
 * @author Jerome Louvel
 */
public abstract class NettyServerHelper extends org.restlet.engine.adapter.HttpServerHelper {

	private ServerBootstrap serverBootstrap;

	private Channel channel;

	private EventLoopGroup bossGroup;

	private EventLoopGroup workerGroup;

	private Processor<HttpRequest, HttpResponse> processor;

	/**
	 * Constructor.
	 * 
	 * @param server
	 *            The server to help.
	 */
	public NettyServerHelper(Server server) {
		super(server);
	}

	protected EventLoopGroup getBossGroup() {
		return bossGroup;
	}

	protected Channel getChannel() {
		return channel;
	}

	public Processor<HttpRequest, HttpResponse> getProcessor() {
		return processor;
	}

	protected ServerBootstrap getServerBootstrap() {
		return serverBootstrap;
	}

	protected EventLoopGroup getWorkerGroup() {
		return workerGroup;
	}

	protected void setBossGroup(EventLoopGroup eventGroup) {
		this.bossGroup = eventGroup;
	}

	protected void setChannel(Channel channel) {
		this.channel = channel;
	}

	public void setProcessor(Processor<HttpRequest, HttpResponse> processor) {
		this.processor = processor;
	}

	protected void setServerBootstrap(ServerBootstrap serverBootstrap) {
		this.serverBootstrap = serverBootstrap;
	}

	protected void setWorkerGroup(EventLoopGroup workerGroup) {
		this.workerGroup = workerGroup;
	}

	@Override
	public void start() throws Exception {
		super.start();
		setBossGroup(new NioEventLoopGroup());
		setWorkerGroup(new NioEventLoopGroup());
		setServerBootstrap(new ServerBootstrap());
		getServerBootstrap().option(ChannelOption.SO_BACKLOG, 1024);
		getServerBootstrap().group(getBossGroup(), getWorkerGroup()).channel(NioServerSocketChannel.class)
				.childOption(ChannelOption.AUTO_READ, false).localAddress(getHelped().getPort())
				.childHandler(new ChannelInitializer<SocketChannel>() {
					@Override
					protected void initChannel(SocketChannel ch) throws Exception {
						ChannelPipeline pipeline = ch.pipeline();

						pipeline.addLast(new HttpRequestDecoder(), new HttpResponseEncoder())
								.addLast("serverStreamsHandler", new HttpStreamsServerHandler());

						HandlerSubscriber<HttpResponse> subscriber = new HandlerSubscriber<>(ch.eventLoop(), 2, 4);
						HandlerPublisher<HttpRequest> publisher = new HandlerPublisher<>(ch.eventLoop(),
								HttpRequest.class);

						pipeline.addLast("serverSubscriber", subscriber);
						pipeline.addLast("serverPublisher", publisher);

						getProcessor().subscribe(subscriber);
						publisher.subscribe(getProcessor());
					}
				});

		setChannel(getServerBootstrap().bind().sync().channel());
		setEphemeralPort(((InetSocketAddress) getChannel().localAddress()).getPort());
		getLogger().info("Starting the Netty " + getProtocols() + " server on port " + getHelped().getPort());
	}

	@Override
	public void stop() throws Exception {
		getLogger().info("Stopping the Netty " + getProtocols() + " server on port " + getHelped().getPort());
		getChannel().close().sync();
		getBossGroup().shutdownGracefully();
		getWorkerGroup().shutdownGracefully();
		super.stop();
	}

}
