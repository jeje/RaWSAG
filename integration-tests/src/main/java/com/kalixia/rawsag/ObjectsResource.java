package com.kalixia.rawsag;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.UUID;

@Path("/objects")
@Produces(MediaType.APPLICATION_JSON)
public class ObjectsResource {

    @GET
    @Path("{id}")
    public UUID echoUUID(@PathParam("id") UUID id) {
        return id;
    }

}
