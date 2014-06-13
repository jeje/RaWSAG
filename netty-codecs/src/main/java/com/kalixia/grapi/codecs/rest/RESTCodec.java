package com.kalixia.grapi.codecs.rest;

import com.kalixia.grapi.ApiRequest;
import com.kalixia.grapi.ApiResponse;
import com.kalixia.grapi.ClientAddressUtil;
import com.kalixia.grapi.MDCLogging;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.netty.handler.codec.http.HttpHeaders.Names.ACCEPT;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaders.Values.KEEP_ALIVE;

@ChannelHandler.Sharable
public class RESTCodec extends MessageToMessageCodec<FullHttpRequest, ApiResponse> {
    private static final Logger LOGGER = LoggerFactory.getLogger(RESTCodec.class);
    public static final String HEADER_REQUEST_ID = "X-Api-Request-ID";

    /**
     * Decode a {@link FullHttpRequest} as a {@link ApiRequest}.
     * @param ctx
     * @param request
     * @param out
     * @throws Exception
     */
    @Override
    protected void decode(ChannelHandlerContext ctx, FullHttpRequest request, List<Object> out) throws Exception {
        UUID requestID;
        String requestIDasString = request.headers().get("X-Api-Request-ID");
        if (requestIDasString != null && !"".equals(requestIDasString)) {
            requestID = UUID.fromString(requestIDasString);
        } else {
            requestID = UUID.randomUUID();
        }
        MDC.put(MDCLogging.MDC_REQUEST_ID, requestID.toString());

        LOGGER.debug("Decoding HTTP request as ApiRequest for {}", request);

        String contentType = request.headers().get(ACCEPT);

        // build headers map
        MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();
        HttpHeaders nettyHeaders = request.headers();
        Iterator<String> iterator = nettyHeaders.names().iterator();
        while (iterator.hasNext()) {
            String headerName = iterator.next();
            headers.put(headerName, nettyHeaders.getAll(headerName));
        }

        // build ApiRequest object
        ApiRequest apiRequest = new ApiRequest(requestID,
                request.getUri(), request.getMethod(),
                ReferenceCountUtil.retain(request.content()), contentType,
                headers, ClientAddressUtil.extractClientAddress(ctx.channel().remoteAddress()));
        out.add(apiRequest);
    }

    /**
     * Encode an {@link ApiResponse} as a {@link FullHttpResponse}.
     * @param ctx
     * @param apiResponse
     * @param out
     * @throws Exception
     */
    @Override
    protected void encode(ChannelHandlerContext ctx, ApiResponse apiResponse, List<Object> out) throws Exception {
        LOGGER.debug("Encoding ApiResponse as HTTP response for {}", apiResponse);

        FullHttpResponse httpResponse = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                apiResponse.status(),
                apiResponse.content());
        // insert usual HTTP headers
        httpResponse.headers().set(CONTENT_LENGTH, apiResponse.content().readableBytes());
        httpResponse.headers().set(CONTENT_TYPE, apiResponse.contentType());
        httpResponse.headers().set(CONNECTION, KEEP_ALIVE);

        // insert request ID header
        if (apiResponse.id() != null) {
            httpResponse.headers().set(HEADER_REQUEST_ID, apiResponse.id().toString());
        }

        // insert headers from ApiResponse object
        Iterator<Map.Entry<String, List<String>>> iterator = apiResponse.headers().entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, List<String>> header = iterator.next();
            httpResponse.headers().set(header.getKey(), header.getValue());
        }

        LOGGER.debug("About to return response {}", httpResponse);

        out.add(httpResponse);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        super.exceptionCaught(ctx, cause);
        LOGGER.error("REST Codec error", cause);
    }
}
