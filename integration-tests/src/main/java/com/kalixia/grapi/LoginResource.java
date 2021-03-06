package com.kalixia.grapi;

import javax.inject.Inject;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.Path;

@Path("/")
@SuppressWarnings("PMD.UnnecessaryConstructor")
public class LoginResource {

    @Inject
    public LoginResource() {
    }

    @GET
    @Path("login")
    public String login(@FormParam("login") String login, @FormParam("password") String password) {
        return String.format("Should login with %s %s!", login, password);
    }

}
