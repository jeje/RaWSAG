package com.kalixia.grapi.codecs.rxjava

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.base.Charsets
import com.kalixia.grapi.ObservableApiResponse
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.HttpContent
import io.netty.handler.codec.http.HttpResponse
import org.glassfish.jersey.internal.util.collection.ImmutableMultivaluedMap
import spock.lang.Specification

import javax.ws.rs.core.MultivaluedMap

import static io.netty.handler.codec.http.HttpResponseStatus.OK
import static javax.ws.rs.core.MediaType.APPLICATION_JSON

class ObservableEncoderTest extends Specification {

    def "encode RxJava Observer as HTTP chunks"() {
        given:
        ObjectMapper objectMapper = new ObjectMapper()
        EmbeddedChannel channel = new EmbeddedChannel(new ObservableEncoder(objectMapper))

        and:
        def messages = [
                new TestObject(dummy1: 'some', dummy2: 'messages'),
                new TestObject(dummy1: 'to be', dummy2: 'tested')
        ]
        rx.Observable observable = rx.Observable.from(messages)
        UUID requestID = UUID.randomUUID()
        MultivaluedMap<String, String> headers = ImmutableMultivaluedMap.empty();
        ObservableApiResponse apiResponses = new ObservableApiResponse(requestID, OK, observable, APPLICATION_JSON, headers)

        when:
        channel.writeOutbound(apiResponses)
        channel.finish()

        then:
        channel.outboundMessages().size() == 1 + 1 + messages.size() + 1
        def HttpResponse httpResponse = channel.readOutbound()
        def HttpContent startListToken = channel.readOutbound()
        def HttpContent chunk1 = channel.readOutbound()
        def HttpContent chunk2 = channel.readOutbound()
        def HttpContent endListToken = channel.readOutbound()
        assert httpResponse.status == OK
        assert startListToken.content().toString(Charsets.UTF_8) == '['
        assert chunk1.content().toString(Charsets.UTF_8) == '{"dummy1":"some","dummy2":"messages"}'
        assert chunk2.content().toString(Charsets.UTF_8) == ',{"dummy1":"to be","dummy2":"tested"}'
        assert endListToken.content().toString(Charsets.UTF_8) == ']'
    }

    def class TestObject implements Serializable {
        def String dummy1
        def String dummy2
    }

}
