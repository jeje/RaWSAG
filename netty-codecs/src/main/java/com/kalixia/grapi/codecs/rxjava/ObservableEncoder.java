package com.kalixia.grapi.codecs.rxjava;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalixia.grapi.ObservableApiResponse;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.HttpHeaderUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Observer;
import rx.Subscription;

import java.nio.charset.Charset;
import java.util.List;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * Encoder transforming RxJava's {@link ObservableApiResponse} into many HTTP objects, through HTTP chunks.
 * <p>
 * This encoder transforms {@link Observable}s into many {@link io.netty.handler.codec.http.HttpMessage}s,
 * hence sends chunked HTTP responses.
 */
@ChannelHandler.Sharable
@SuppressWarnings({"PMD.AvoidPrefixingMethodParameters", "PMD.DataflowAnomalyAnalysis"})
public class ObservableEncoder extends MessageToMessageEncoder<ObservableApiResponse<?>> {
    private final ObjectMapper objectMapper;
    private static final ByteBuf LIST_BEGIN = Unpooled.wrappedBuffer("[".getBytes(Charset.defaultCharset()));
    private static final ByteBuf LIST_END   = Unpooled.wrappedBuffer("]".getBytes(Charset.defaultCharset()));
    private static final ByteBuf LIST_ITEM_SEPARATOR = Unpooled.wrappedBuffer(",".getBytes(Charset.defaultCharset()));
    private static final Logger LOGGER = LoggerFactory.getLogger(ObservableEncoder.class);

    public ObservableEncoder(ObjectMapper objectMapper) {
        super();
        this.objectMapper = objectMapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void encode(final ChannelHandlerContext ctx, final ObservableApiResponse<?> apiResponse,
                          final List<Object> out) throws Exception {
        DefaultHttpResponse response = new DefaultHttpResponse(HTTP_1_1, apiResponse.status());
        HttpHeaderUtil.setTransferEncodingChunked(response, true);
        response.headers().set(CONTENT_TYPE, apiResponse.contentType());
        // insert request ID header
        if (apiResponse.id() != null) {
            response.headers().set("X-Api-Request-ID", apiResponse.id().toString());
        }
        out.add(response);

        out.add(new DefaultHttpContent(LIST_BEGIN));

        Observable observable = apiResponse.observable();
        Subscription subscription = observable.subscribe(new Observer() {
            private boolean first = true;

            @Override
            public void onNext(Object args) {
                try {
                    byte[] content = objectMapper.writeValueAsBytes(args);
                    ByteBuf buffer;
                    if (first) {
                        buffer = Unpooled.wrappedBuffer(content);
                        first = false;
                    } else {
                        buffer = Unpooled.wrappedBuffer(
                                Unpooled.copiedBuffer(LIST_ITEM_SEPARATOR),
                                Unpooled.wrappedBuffer(content));
                    }
                    DefaultHttpContent chunk = new DefaultHttpContent(buffer);
                    out.add(chunk);
                } catch (Throwable t) {
                    ctx.fireExceptionCaught(t);
                }
            }

            @Override
            public void onCompleted() {
                DefaultLastHttpContent lastChunk = new DefaultLastHttpContent(LIST_END);
                out.add(lastChunk);
            }

            @Override
            public void onError(Throwable t) {
                LOGGER.error("Unexpected error while processing Observable", t);
                ctx.fireExceptionCaught(t);
            }

        });
        subscription.unsubscribe();
    }

}
