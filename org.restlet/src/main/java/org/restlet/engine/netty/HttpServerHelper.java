/**
 * Copyright 2005-2016 Restlet
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
import org.restlet.Context;
import org.restlet.Restlet;
import org.restlet.Server;
import org.restlet.engine.Engine;
import org.restlet.engine.header.HeaderUtils;
import org.restlet.message.Method;
import org.restlet.message.Request;
import org.restlet.message.Response;
import org.restlet.message.Status;
import org.restlet.representation.MediaType;
import org.restlet.util.Header;
import org.restlet.util.Protocol;
import org.restlet.util.Series;

import com.typesafe.netty.HandlerSubscriber;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

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

    private volatile HandlerSubscriber<? super HttpResponse> subscriber;

    private volatile Subscription publisherSubscription;

    /**
     * Constructor.
     * 
     * @param server
     *            The server to help.
     */
    public HttpServerHelper(Server server) {
        super(server);
        getProtocols().add(Protocol.HTTP);
        this.subscriber = null;
        this.publisherSubscription = null;
    }

    public Subscription getPublisherSubscription() {
        return publisherSubscription;
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
    public void onNext(HttpRequest nettyRequest) {
        System.out.println("onNext: " + nettyRequest);
        HttpServerRequest request = null;
        Response response = null;

        try {
            request = new HttpServerRequest(getContext(), getServerChannel(), nettyRequest);
            response = new Response(request);

            // Effectively handle the request
            getHelped().handle(request, response);

            if (!response.isCommitted() && response.isAutoCommitting()) {
                response.setCommitted(true);
            }

            if (response.isCommitted()) {
                FullHttpResponse nettyResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                        HttpResponseStatus.OK);
                Series<Header> headers = new Series<>(Header.class);

                try {
                    // Add all the necessary headers
                    HeaderUtils.addGeneralHeaders(response, headers);
                    HeaderUtils.addResponseHeaders(response, headers);

                    // Set the status code in the response
                    if (response.getStatus() != null) {
                        nettyResponse.setStatus(new HttpResponseStatus(response.getStatus().getCode(),
                                response.getStatus().getReasonPhrase()));
                    }

                    if ((response.getRequest().getMethod() != null)
                            && response.getRequest().getMethod().equals(Method.HEAD)) {
                        HeaderUtils.addEntityHeaders(response, headers);
                        response.setEntity(null);
                    } else if (Method.GET.equals(response.getRequest().getMethod())
                            && Status.SUCCESS_OK.equals(response.getStatus())
                            && (!response.isEntityAvailable())) {
                        HeaderUtils.addEntityHeaders(response, headers);
                        getLogger()
                                .warn(
                                        "A response with a 200 (Ok) status should have an entity. Make sure that resource \""
                                                + response.getRequest()
                                                        .getResourceRef()
                                                + "\" returns one or sets the status to 204 (No content).");
                    } else if (response.getStatus().equals(Status.SUCCESS_NO_CONTENT)) {
                        HeaderUtils.addEntityHeaders(response, headers);

                        if (response.isEntityAvailable()) {
                            getLogger()
                                    .debug("Responses with a 204 (No content) status generally don't have an entity. Only adding entity headers for resource \""
                                            + response.getRequest().getResourceRef()
                                            + "\".");
                            response.setEntity(null);
                        }
                    } else if (response.getStatus()
                            .equals(Status.SUCCESS_RESET_CONTENT)) {
                        if (response.isEntityAvailable()) {
                            getLogger()
                                    .warn(
                                            "Responses with a 205 (Reset content) status can't have an entity. Ignoring the entity for resource \""
                                                    + response.getRequest()
                                                            .getResourceRef()
                                                    + "\".");
                            response.setEntity(null);
                        }
                    } else if (response.getStatus().equals(
                            Status.REDIRECTION_NOT_MODIFIED)) {
                        if (response.getEntity() != null) {
                            HeaderUtils.addNotModifiedEntityHeaders(response
                                    .getEntity(), headers);
                            response.setEntity(null);
                        }
                    } else if (response.getStatus().isInformational()) {
                        if (response.isEntityAvailable()) {
                            getLogger()
                                    .warn(
                                            "Responses with an informational (1xx) status can't have an entity. Ignoring the entity for resource \""
                                                    + response.getRequest()
                                                            .getResourceRef()
                                                    + "\".");
                            response.setEntity(null);
                        }
                    } else {
                        HeaderUtils.addEntityHeaders(response, headers);

                        if (!response.isEntityAvailable()) {
                            if ((response.getEntity() != null)
                                    && (response.getEntity().getSize() != 0)) {
                                getLogger()
                                        .warn(
                                                "A response with an unavailable and potentially non empty entity was returned. Ignoring the entity for resource \""
                                                        + response.getRequest()
                                                                .getResourceRef()
                                                        + "\".");
                            }

                            response.setEntity(null);
                        }
                    }

                    // Add the response headers
                    HeaderUtils.addResponseHeaders(response, headers);

                    // Copy Restlet headers to Netty headers
                    for (Header header : headers) {
                        nettyResponse.headers().add(header.getName(), header.getValue());
                    }

                    // Copy the content (NON OPTIMAL)
                    if (response.getEntity() != null) {
                        nettyResponse = nettyResponse
                                .replace(Unpooled.wrappedBuffer(response.getEntity().getText().getBytes()));
                    }

                    // Send the response to the client
                    subscriber.onNext(nettyResponse);
                } catch (Exception e) {
                    Context.getCurrentLogger().warn("Exception intercepted while adding the response headers",
                            e);
                    response.setStatus(Status.SERVER_ERROR_INTERNAL);
                } finally {
                    if (response.getOnSent() != null) {
                        response.getOnSent().handle(response.getRequest(), response);
                    }
                }
            }
        } catch (Throwable t) {
            getLogger().warn("Error while handling an HTTP server call", t);
            response.setStatus(Status.SERVER_ERROR_INTERNAL, t);
            onError(t);
        } finally {
            Engine.clearThreadLocalVariables();
        }
    }

    @Override
    public void onSubscribe(Subscription s) {
        System.out.println("onSubscribe: " + s);
        this.publisherSubscription = s;
        s.request(Long.MAX_VALUE);
    }

    public void setPublisherSubscription(Subscription publisherSubscription) {
        this.publisherSubscription = publisherSubscription;
    }

    public void setSubscriber(Subscriber<? super HttpResponse> subscriber) {
        this.subscriber = (HandlerSubscriber<? super HttpResponse>) subscriber;
    }

    @Override
    public void subscribe(Subscriber<? super HttpResponse> s) {
        System.out.println("subscribe: " + s);
        Subscription subscriberSubscription = new Subscription() {

            @Override
            public void cancel() {
                System.out.println("cancel: " + this);
            }

            @Override
            public void request(long n) {
                System.out.println("request: " + this);
            }

        };

        this.subscriber = (HandlerSubscriber<? super HttpResponse>) s;
        this.subscriber.onSubscribe(subscriberSubscription);
    }

}
