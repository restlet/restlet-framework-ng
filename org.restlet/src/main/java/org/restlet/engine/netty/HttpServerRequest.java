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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import org.restlet.Context;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.data.CacheDirective;
import org.restlet.data.ChallengeResponse;
import org.restlet.data.ClientInfo;
import org.restlet.data.Conditions;
import org.restlet.data.Cookie;
import org.restlet.data.Header;
import org.restlet.data.Method;
import org.restlet.data.Protocol;
import org.restlet.data.Range;
import org.restlet.data.RecipientInfo;
import org.restlet.data.Reference;
import org.restlet.data.Tag;
import org.restlet.data.Warning;
import org.restlet.engine.header.CacheDirectiveReader;
import org.restlet.engine.header.CookieReader;
import org.restlet.engine.header.ExpectationReader;
import org.restlet.engine.header.HeaderConstants;
import org.restlet.engine.header.HeaderReader;
import org.restlet.engine.header.PreferenceReader;
import org.restlet.engine.header.RangeReader;
import org.restlet.engine.header.RecipientInfoReader;
import org.restlet.engine.header.StringReader;
import org.restlet.engine.header.WarningReader;
import org.restlet.engine.security.AuthenticatorUtils;
import org.restlet.engine.util.DateUtils;
import org.restlet.engine.util.ReferenceUtils;
import org.restlet.representation.Representation;
import org.restlet.util.Series;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpRequest;

/**
 * Request wrapper for server HTTP calls.
 * 
 * @author Jerome Louvel
 */
public class HttpServerRequest extends Request {

    /** Indicates if the headers were parsed and added. */
    private volatile boolean headersAdded;

    /** Indicates if the host string was parsed. */
    private volatile boolean hostParsed;

    /**
     * Indicates if the access control data for request headers was parsed and
     * added
     */
    private volatile boolean accessControlRequestHeadersAdded;

    /**
     * Indicates if the access control data for request methods was parsed and
     * added
     */
    private volatile boolean accessControlRequestMethodAdded;

    /** Indicates if the cache control data was parsed and added. */
    private volatile boolean cacheDirectivesAdded;

    /** Indicates if the client data was parsed and added. */
    private volatile boolean clientAdded;

    /** Indicates if the conditions were parsed and added. */
    private volatile boolean conditionAdded;

    /** The context of the HTTP server connector that issued the call. */
    private volatile Context context;

    /** Indicates if the cookies were parsed and added. */
    private volatile boolean cookiesAdded;

    /** Indicates if the request entity was added. */
    private volatile boolean entityAdded;

    /** The low-level HTTP channel. */
    private Channel nettyChannel;

    /** The low-level HTTP request. */
    private volatile HttpRequest nettyRequest;

    /** Indicates if the proxy security data was parsed and added. */
    private volatile boolean proxySecurityAdded;

    /** Indicates if the ranges data was parsed and added. */
    private volatile boolean rangesAdded;

    /** Indicates if the recipients info was parsed and added. */
    private volatile boolean recipientsInfoAdded;

    /** Indicates if the referrer was parsed and added. */
    private volatile boolean referrerAdded;

    /** Indicates if the security data was parsed and added. */
    private volatile boolean securityAdded;

    /** Indicates if the warning data was parsed and added. */
    private volatile boolean warningsAdded;

    /** The hostRef domain. */
    private volatile String hostDomain;

    /** The hostRef port. */
    private volatile int hostPort;

    /**
     * Constructor.
     * 
     * @param context
     *            The context of the HTTP server connector that issued the call.
     * @param nettyRequest
     *            The low-level HTTP request.
     * @param nettyChannel
     *            The low-level HTTP channel.
     */
    public HttpServerRequest(Context context, Channel nettyChannel, HttpRequest nettyRequest) {
        this.context = context;
        this.clientAdded = false;
        this.conditionAdded = false;
        this.cookiesAdded = false;
        this.entityAdded = false;
        this.headersAdded = false;
        this.hostParsed = false;
        this.nettyChannel = nettyChannel;
        this.nettyRequest = nettyRequest;
        this.referrerAdded = false;
        this.securityAdded = false;
        this.proxySecurityAdded = false;
        this.recipientsInfoAdded = false;
        this.warningsAdded = false;

        // Set the properties
        setMethod(Method.valueOf(nettyRequest.method().name()));
        setProtocol(isConfidential() ? Protocol.HTTPS : Protocol.HTTP);

        // Set the host reference
        StringBuilder sb = new StringBuilder();
        sb.append(getProtocol().getSchemeName()).append("://");
        sb.append(getHostDomain());
        if ((getHostPort() != -1) && (getHostPort() != getProtocol().getDefaultPort())) {
            sb.append(':').append(getHostPort());
        }
        setHostRef(sb.toString());

        String uri = getNettyRequest().uri();

        // Set the resource reference
        if (uri != null) {
            setResourceRef(new Reference(getHostRef(), uri));

            if (getResourceRef().isRelative()) {
                // Take care of the "/" between the host part and the segments.
                if (!uri.startsWith("/")) {
                    setResourceRef(new Reference(getHostRef().toString() + "/" + uri));
                } else {
                    setResourceRef(new Reference(getHostRef().toString() + uri));
                }
            }

            setOriginalRef(ReferenceUtils.getOriginalRef(getResourceRef(), getHeaders()));
        }

        // Set the request date
        String dateHeader = getHeaders().getFirstValue(HeaderConstants.HEADER_DATE, true);
        Date date = null;
        if (dateHeader != null) {
            date = DateUtils.parse(dateHeader);
        }

        if (date == null) {
            date = new Date();
        }

        setDate(date);
    }

    @Override
    public boolean abort() {
        try {
            getNettyChannel().close().sync();
            return true;
        } catch (InterruptedException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public void commit(Response response) {
        getNettyChannel().flush();
    }

    @Override
    public void flushBuffers() throws IOException {
        getNettyChannel().flush();
    }

    @Override
    public Set<String> getAccessControlRequestHeaders() {
        Set<String> result = super.getAccessControlRequestHeaders();
        if (!accessControlRequestHeadersAdded) {
            for (String header : getHeaders().getValuesArray(HeaderConstants.HEADER_ACCESS_CONTROL_REQUEST_HEADERS,
                    true)) {
                new StringReader(header).addValues(result);
            }
            accessControlRequestHeadersAdded = true;
        }
        return result;
    }

    @Override
    public Method getAccessControlRequestMethod() {
        Method result = super.getAccessControlRequestMethod();
        if (!accessControlRequestMethodAdded) {
            String header = getHeaders().getFirstValue(HeaderConstants.HEADER_ACCESS_CONTROL_REQUEST_METHOD, true);
            if (header != null) {
                result = Method.valueOf(header);
                super.setAccessControlRequestMethod(result);
            }
            accessControlRequestMethodAdded = true;
        }
        return result;
    }

    @Override
    public List<CacheDirective> getCacheDirectives() {
        List<CacheDirective> result = super.getCacheDirectives();

        if (!cacheDirectivesAdded) {
            for (Header header : getHeaders().subList(HeaderConstants.HEADER_CACHE_CONTROL)) {
                new CacheDirectiveReader(header.getValue()).addValues(result);
            }

            cacheDirectivesAdded = true;
        }

        return result;
    }

    @Override
    public ChallengeResponse getChallengeResponse() {
        ChallengeResponse result = super.getChallengeResponse();

        if (!this.securityAdded) {
            // Extract the header value
            String authorization = getHeaders().getValues(HeaderConstants.HEADER_AUTHORIZATION);

            // Set the challenge response
            result = AuthenticatorUtils.parseResponse(this, authorization, getHeaders());
            setChallengeResponse(result);
            this.securityAdded = true;
        }

        return result;
    }

    public String getClientAddress() {
        InetSocketAddress isa = (InetSocketAddress) getNettyChannel().remoteAddress();
        return isa.getHostString();
    }

    /**
     * Returns the client-specific information.
     * 
     * @return The client-specific information.
     */
    @Override
    public ClientInfo getClientInfo() {
        final ClientInfo result = super.getClientInfo();

        if (!this.clientAdded) {
            // Extract the header values
            String acceptMediaType = getHeaders().getValues(HeaderConstants.HEADER_ACCEPT);
            String acceptCharset = getHeaders().getValues(HeaderConstants.HEADER_ACCEPT_CHARSET);
            String acceptEncoding = getHeaders().getValues(HeaderConstants.HEADER_ACCEPT_ENCODING);
            String acceptLanguage = getHeaders().getValues(HeaderConstants.HEADER_ACCEPT_LANGUAGE);
            String acceptPatch = getHeaders().getValues(HeaderConstants.HEADER_ACCEPT_PATCH);
            String expect = getHeaders().getValues(HeaderConstants.HEADER_EXPECT);

            // Parse the headers and update the call preferences

            // Parse the Accept* headers. If an error occurs during the parsing
            // of each header, the error is traced and we keep on with the other
            // headers.
            try {
                PreferenceReader.addCharacterSets(acceptCharset, result);
            } catch (Exception e) {
                this.context.getLogger().info(e.getMessage());
            }

            try {
                PreferenceReader.addEncodings(acceptEncoding, result);
            } catch (Exception e) {
                this.context.getLogger().info(e.getMessage());
            }

            try {
                PreferenceReader.addLanguages(acceptLanguage, result);
            } catch (Exception e) {
                this.context.getLogger().info(e.getMessage());
            }

            try {
                PreferenceReader.addMediaTypes(acceptMediaType, result);
            } catch (Exception e) {
                this.context.getLogger().info(e.getMessage());
            }

            try {
                PreferenceReader.addPatches(acceptPatch, result);
            } catch (Exception e) {
                this.context.getLogger().info(e.getMessage());
            }

            try {
                ExpectationReader.addValues(expect, result);
            } catch (Exception e) {
                this.context.getLogger().info(e.getMessage());
            }

            // Set other properties
            result.setAgent(getHeaders().getValues(HeaderConstants.HEADER_USER_AGENT));
            result.setFrom(getHeaders().getFirstValue(HeaderConstants.HEADER_FROM, true));
            result.setAddress(getClientAddress());
            result.setPort(getClientPort());

            if (this.context != null) {
                // Special handling for the non standard but common
                // "X-Forwarded-For" header.
                final boolean useForwardedForHeader = Boolean
                        .parseBoolean(this.context.getParameters().getFirstValue("useForwardedForHeader", false));
                if (useForwardedForHeader) {
                    // Lookup the "X-Forwarded-For" header supported by popular
                    // proxies and caches.
                    final String header = getHeaders().getValues(HeaderConstants.HEADER_X_FORWARDED_FOR);
                    if (header != null) {
                        final String[] addresses = header.split(",");
                        for (int i = 0; i < addresses.length; i++) {
                            String address = addresses[i].trim();
                            result.getForwardedAddresses().add(address);
                        }
                    }
                }
            }

            this.clientAdded = true;
        }

        return result;
    }

    public int getClientPort() {
        InetSocketAddress isa = (InetSocketAddress) getNettyChannel().remoteAddress();
        return isa.getPort();
    }

    /**
     * Returns the condition data applying to this call.
     * 
     * @return The condition data applying to this call.
     */
    @Override
    public Conditions getConditions() {
        final Conditions result = super.getConditions();

        if (!this.conditionAdded) {
            // Extract the header values
            String ifMatchHeader = getHeaders().getValues(HeaderConstants.HEADER_IF_MATCH);
            String ifNoneMatchHeader = getHeaders().getValues(HeaderConstants.HEADER_IF_NONE_MATCH);
            Date ifModifiedSince = null;
            Date ifUnmodifiedSince = null;
            String ifRangeHeader = getHeaders().getFirstValue(HeaderConstants.HEADER_IF_RANGE, true);

            for (Header header : getHeaders()) {
                if (header.getName().equalsIgnoreCase(HeaderConstants.HEADER_IF_MODIFIED_SINCE)) {
                    ifModifiedSince = HeaderReader.readDate(header.getValue(), false);
                } else if (header.getName().equalsIgnoreCase(HeaderConstants.HEADER_IF_UNMODIFIED_SINCE)) {
                    ifUnmodifiedSince = HeaderReader.readDate(header.getValue(), false);
                }
            }

            // Set the If-Modified-Since date
            if ((ifModifiedSince != null) && (ifModifiedSince.getTime() != -1)) {
                result.setModifiedSince(ifModifiedSince);
            }

            // Set the If-Unmodified-Since date
            if ((ifUnmodifiedSince != null) && (ifUnmodifiedSince.getTime() != -1)) {
                result.setUnmodifiedSince(ifUnmodifiedSince);
            }

            // Set the If-Match tags
            List<Tag> match = null;
            Tag current = null;
            if (ifMatchHeader != null) {
                try {
                    HeaderReader<Object> hr = new HeaderReader<Object>(ifMatchHeader);
                    String value = hr.readRawValue();

                    while (value != null) {
                        current = Tag.parse(value);

                        // Is it the first tag?
                        if (match == null) {
                            match = new ArrayList<Tag>();
                            result.setMatch(match);
                        }

                        // Add the new tag
                        match.add(current);

                        // Read the next token
                        value = hr.readRawValue();
                    }
                } catch (Exception e) {
                    this.context.getLogger().info("Unable to process the if-match header: " + ifMatchHeader, e);
                }
            }

            // Set the If-None-Match tags
            List<Tag> noneMatch = null;
            if (ifNoneMatchHeader != null) {
                try {
                    HeaderReader<Object> hr = new HeaderReader<Object>(ifNoneMatchHeader);
                    String value = hr.readRawValue();

                    while (value != null) {
                        current = Tag.parse(value);

                        // Is it the first tag?
                        if (noneMatch == null) {
                            noneMatch = new ArrayList<Tag>();
                            result.setNoneMatch(noneMatch);
                        }

                        noneMatch.add(current);

                        // Read the next token
                        value = hr.readRawValue();
                    }
                } catch (Exception e) {
                    this.context.getLogger().info("Unable to process the if-none-match header: " + ifNoneMatchHeader,
                            e);
                }
            }

            if (ifRangeHeader != null && ifRangeHeader.length() > 0) {
                Tag tag = Tag.parse(ifRangeHeader);

                if (tag != null) {
                    result.setRangeTag(tag);
                } else {
                    Date date = HeaderReader.readDate(ifRangeHeader, false);
                    result.setRangeDate(date);
                }
            }

            this.conditionAdded = true;
        }

        return result;
    }

    /**
     * Returns the cookies provided by the client.
     * 
     * @return The cookies provided by the client.
     */
    @Override
    public Series<Cookie> getCookies() {
        Series<Cookie> result = super.getCookies();

        if (!this.cookiesAdded) {
            String cookieValues = getHeaders().getValues(HeaderConstants.HEADER_COOKIE);

            if (cookieValues != null) {
                new CookieReader(cookieValues).addValues(result);
            }

            this.cookiesAdded = true;
        }

        return result;
    }

    /**
     * Returns the representation provided by the client.
     * 
     * @return The representation provided by the client.
     */
    @Override
    public Representation getEntity() {
        if (!this.entityAdded) {
            // setEntity(((ServerCall) getHttpCall()).getRequestEntity());
            this.entityAdded = true;
        }

        return super.getEntity();
    }

    @Override
    public Series<Header> getHeaders() {
        final Series<Header> result = super.getHeaders();

        if (!this.headersAdded) {
            Entry<CharSequence, CharSequence> header = null;
            for (Iterator<Entry<CharSequence, CharSequence>> headers = getNettyRequest().headers()
                    .iteratorCharSequence(); headers.hasNext();) {
                header = headers.next();
                result.add(header.getKey().toString(), header.getValue().toString());
            }
            this.headersAdded = true;
        }

        return result;
    }

    /**
     * Returns the host domain name.
     * 
     * @return The host domain name.
     */
    public String getHostDomain() {
        if (!this.hostParsed) {
            parseHost();
        }
        return this.hostDomain;
    }

    /**
     * Returns the host port.
     * 
     * @return The host port.
     */
    public int getHostPort() {
        if (!this.hostParsed) {
            parseHost();
        }
        return this.hostPort;
    }

    /**
     * Returns the low-level Netty channel.
     * 
     * @return The low-level Netty channel.
     */
    public Channel getNettyChannel() {
        return this.nettyChannel;
    }

    /**
     * Returns the low-level Netty request.
     * 
     * @return The low-level Netty request.
     */
    public HttpRequest getNettyRequest() {
        return this.nettyRequest;
    }

    @Override
    public ChallengeResponse getProxyChallengeResponse() {
        ChallengeResponse result = super.getProxyChallengeResponse();

        if (!this.proxySecurityAdded) {
            // Extract the header value
            final String authorization = getHeaders().getValues(HeaderConstants.HEADER_PROXY_AUTHORIZATION);

            // Set the challenge response
            result = AuthenticatorUtils.parseResponse(this, authorization, getHeaders());
            setProxyChallengeResponse(result);
            this.proxySecurityAdded = true;
        }

        return result;
    }

    @Override
    public List<Range> getRanges() {
        final List<Range> result = super.getRanges();

        if (!this.rangesAdded) {
            // Extract the header value
            final String ranges = getHeaders().getValues(HeaderConstants.HEADER_RANGE);
            result.addAll(RangeReader.read(ranges));

            this.rangesAdded = true;
        }

        return result;
    }

    @Override
    public List<RecipientInfo> getRecipientsInfo() {
        List<RecipientInfo> result = super.getRecipientsInfo();
        if (!recipientsInfoAdded) {
            for (String header : getHeaders().getValuesArray(HeaderConstants.HEADER_VIA, true)) {
                new RecipientInfoReader(header).addValues(result);
            }
            recipientsInfoAdded = true;
        }
        return result;
    }

    /**
     * Returns the referrer reference if available.
     * 
     * @return The referrer reference.
     */
    @Override
    public Reference getReferrerRef() {
        if (!this.referrerAdded) {
            final String referrerValue = getHeaders().getValues(HeaderConstants.HEADER_REFERRER);
            if (referrerValue != null) {
                setReferrerRef(new Reference(referrerValue));
            }

            this.referrerAdded = true;
        }

        return super.getReferrerRef();
    }

    public String getVersion() {
        String result = null;
        final int index = getNettyRequest().protocolVersion().text().indexOf('/');

        if (index != -1) {
            result = getNettyRequest().protocolVersion().text().substring(index + 1);
        }

        return result;
    }

    @Override
    public List<Warning> getWarnings() {
        List<Warning> result = super.getWarnings();
        if (!warningsAdded) {
            for (String header : getHeaders().getValuesArray(HeaderConstants.HEADER_WARNING, true)) {
                new WarningReader(header).addValues(result);
            }
            warningsAdded = true;
        }
        return result;
    }

    /**
     * Parses the "host" header to set the server host and port properties.
     */
    private void parseHost() {
        String host = getHeaders().getFirstValue(HeaderConstants.HEADER_HOST, true);

        if (host != null) {
            // Take care of IPV6 addresses
            int colonIndex = host.indexOf(':', host.indexOf(']'));

            if (colonIndex != -1) {
                setHostDomain(host.substring(0, colonIndex));
                setHostPort(Integer.valueOf(host.substring(colonIndex + 1)));
            } else {
                setHostDomain(host);
                setHostPort(getProtocol().getDefaultPort());
            }
        } else {
            this.context.getLogger().info("Couldn't find the mandatory \"Host\" HTTP header.");
        }

        this.hostParsed = true;
    }

    @Override
    public void setAccessControlRequestHeaders(Set<String> accessControlRequestHeaders) {
        super.setAccessControlRequestHeaders(accessControlRequestHeaders);
        this.accessControlRequestHeadersAdded = true;
    }

    @Override
    public void setAccessControlRequestMethod(Method accessControlRequestMethod) {
        super.setAccessControlRequestMethod(accessControlRequestMethod);
        this.accessControlRequestMethodAdded = true;
    }

    @Override
    public void setChallengeResponse(ChallengeResponse response) {
        super.setChallengeResponse(response);
        this.securityAdded = true;
    }

    @Override
    public void setEntity(Representation entity) {
        super.setEntity(entity);
        this.entityAdded = true;
    }

    /**
     * Sets the host domain name.
     * 
     * @param hostDomain
     *            The baseRef domain name.
     */
    public void setHostDomain(String hostDomain) {
        this.hostDomain = hostDomain;
    }

    /**
     * Sets the host port.
     * 
     * @param hostPort
     *            The host port.
     */
    public void setHostPort(int hostPort) {
        this.hostPort = hostPort;
    }

    /**
     * Sets the low-level Netty channel.
     * 
     * @param nettyChannel
     *            The low-level Netty channel.
     */
    public void setNettyChannel(Channel nettyChannel) {
        this.nettyChannel = nettyChannel;
    }

    /**
     * Sets the low-level Netty request.
     * 
     * @param nettyRequest
     *            The low-level Netty request.
     */
    public void setNettyRequest(HttpRequest nettyRequest) {
        this.nettyRequest = nettyRequest;
    }

    @Override
    public void setProxyChallengeResponse(ChallengeResponse response) {
        super.setProxyChallengeResponse(response);
        this.proxySecurityAdded = true;
    }

    @Override
    public void setRecipientsInfo(List<RecipientInfo> recipientsInfo) {
        super.setRecipientsInfo(recipientsInfo);
        this.recipientsInfoAdded = true;
    }

    @Override
    public void setWarnings(List<Warning> warnings) {
        super.setWarnings(warnings);
        this.warningsAdded = true;
    }
}
