package com.kalixia.grapi.codecs.rest;

import com.kalixia.grapi.ApiRequest;
import com.kalixia.grapi.ApiResponse;
import com.kalixia.grapi.ClientAddressUtil;
import com.kalixia.grapi.MDCLogging;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.util.ReferenceCountUtil;
import org.omg.CORBA.INVALID_ACTIVITY;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URL;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static io.netty.handler.codec.http.HttpHeaders.Names.ACCEPT;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaders.Values.KEEP_ALIVE;

@ChannelHandler.Sharable
public class RESTCodec extends MessageToMessageCodec<FullHttpRequest, ApiResponse> {
    public static final String HEADER_REQUEST_ID = "X-Api-Request-ID";
    private static ByteBuf INVALID_REQUEST_ID;
    private static final Logger LOGGER = LoggerFactory.getLogger(RESTCodec.class);

    static {
        try {
            INVALID_REQUEST_ID = Unpooled.wrappedBuffer(
                    String.format("%s should be a UUID", HEADER_REQUEST_ID).getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            INVALID_REQUEST_ID = Unpooled.EMPTY_BUFFER;
        }
    }

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
        String requestIDasString = request.headers().get(HEADER_REQUEST_ID);
        if (requestIDasString != null && !"".equals(requestIDasString)) {
            try {
                requestID = UUID.fromString(requestIDasString);
            } catch (Exception e) {
                FullHttpResponse httpResponse = new DefaultFullHttpResponse(
                        HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST, INVALID_REQUEST_ID);
                ctx.writeAndFlush(httpResponse).addListener(ChannelFutureListener.CLOSE);
                return;
            }
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

        // build form parameters
        MultivaluedMap<String, String> formParameters = new MultivaluedHashMap<>();
        if (HttpMethod.POST.equals(request.getMethod())) {
            HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(request);
            List<InterfaceHttpData> dataList = decoder.getBodyHttpDatas();
            for (InterfaceHttpData data : dataList) {
                Attribute attribute = (Attribute) data;
                String paramName = attribute.getName();
                String paramValue = attribute.getValue();
                formParameters.add(paramName, paramValue);
            }
            decoder.destroy();
        }

        // build query parameters
        MultivaluedMap<String, String> queryParameters = new MultivaluedHashMap<>();
        QueryStringDecoder queryStringDecoder = new QueryStringDecoder(request.getUri());
        Set<Map.Entry<String, List<String>>> params = queryStringDecoder.parameters().entrySet();
        for (Map.Entry<String, List<String>> param : params) {
            queryParameters.addAll(param.getKey(), param.getValue());
        }

        if (HttpMethod.POST.equals(request.getMethod())) {
            HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(request);
            List<InterfaceHttpData> dataList = decoder.getBodyHttpDatas();
            for (InterfaceHttpData data : dataList) {
                Attribute attribute = (Attribute) data;
                String paramName = attribute.getName();
                String paramValue = attribute.getValue();
                formParameters.add(paramName, paramValue);
            }
            decoder.destroy();
        }

        // build ApiRequest object
        ApiRequest apiRequest = new ApiRequest(requestID,
                request.getUri(), request.getMethod(),
                ReferenceCountUtil.retain(request.content()), contentType,
                headers, formParameters, queryParameters,
                ClientAddressUtil.extractClientAddress(ctx.channel().remoteAddress()));
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
