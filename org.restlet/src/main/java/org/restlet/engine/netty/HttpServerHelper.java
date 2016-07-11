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

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.Restlet;
import org.restlet.Server;
import org.restlet.data.MediaType;
import org.restlet.data.Protocol;
import org.restlet.engine.Engine;

import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;

/**
 * Netty HTTP server connector.
 * 
 * @see <a href="http://netty.io/">Netty home page</a>
 * @author Jerome Louvel
 */
public class HttpServerHelper extends NettyServerHelper {

	public static void main(String[] args) throws Exception {
		Engine.getInstance().getRegisteredServers().add(0, new HttpServerHelper(null));
		Server server = new Server(Protocol.HTTP, 8080);
		server.setNext(new Restlet() {
			@Override
			public void handle(Request request, Response response) {
				super.handle(request, response);
				response.setEntity("Hello, world!", MediaType.TEXT_PLAIN);
			}
		});

		server.start();
	}

	private Subscriber<? super HttpResponse> subscriber;

	private volatile NettyServerCall call;

	/**
	 * Constructor.
	 * 
	 * @param server
	 *            The server to help.
	 */
	public HttpServerHelper(Server server) {
		super(server);
		getProtocols().add(Protocol.HTTP);
	}

	public Subscriber<? super HttpResponse> getSubscriber() {
		return subscriber;
	}

	@Override
	public void onComplete() {
		System.out.println("onComplete");
	}

	@Override
	public void onError(Throwable t) {
		System.out.println("onError: ");
		t.printStackTrace();
	}

	@Override
	public void onNext(HttpRequest request) {
		System.out.println("onNext: " + request);

		try {
			call = new NettyServerCall(getHelped(), getChannel(), request);
			handle(call);

			// if (HttpUtil.is100ContinueExpected(request)) {
			// HttpUtil.set100ContinueExpected(request, true);
			// }
			//
			// appendDecoderResult(t);
			//
			// } else if (msg instanceof HttpContent) {
			// HttpContent httpContent = (HttpContent) msg;
			// ctx.channel().config().setAutoRead(false);
			//
			// if (call != null) {
			// call.onContent(httpContent);
			// } else {
			// throw new IOException(
			// "Unexpected error, content arrived before call created");
			// }
			//
			// if (msg instanceof LastHttpContent) {
			// LastHttpContent trailer = (LastHttpContent) msg;
			//
			// if (!trailer.trailingHeaders().isEmpty()) {
			// // TODO
			// }
			// }
			// }
		} catch (Throwable t) {
			onError(t);
		}
	}

	@Override
	public void onSubscribe(Subscription s) {
		System.out.println("onSubscribe: " + s);
		s.request(Long.MAX_VALUE);
	}

	public void setSubscriber(Subscriber<? super HttpResponse> subscriber) {
		this.subscriber = subscriber;
	}

	@Override
	public void subscribe(Subscriber<? super HttpResponse> s) {
		System.out.println("subscribe: " + s);
		this.subscriber = s;
	}

}
