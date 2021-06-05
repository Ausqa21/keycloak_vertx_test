package com.godric.keycloak_test;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.ext.auth.oauth2.OAuth2Auth;
import io.vertx.ext.auth.oauth2.OAuth2FlowType;
import io.vertx.ext.auth.oauth2.OAuth2Options;
import io.vertx.ext.auth.oauth2.providers.KeycloakAuth;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.handler.CSRFHandler;
import io.vertx.ext.web.handler.OAuth2AuthHandler;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.sstore.LocalSessionStore;
import io.vertx.ext.web.sstore.SessionStore;

public class MainVerticle extends AbstractVerticle {

  @Override
  public void start(Promise<Void> startPromise) throws Exception {
    // Create router
    Router router = Router.router(vertx);

    // Store session information on the server side
    SessionStore sessionStore = LocalSessionStore.create(vertx);
    SessionHandler sessionHandler = SessionHandler.create(sessionStore);

    router.route().handler(sessionHandler);

    // CSRF handler setup required for logout form
    String csrfSecret = "qwerty123";
    CSRFHandler csrfHandler = CSRFHandler.create(vertx, csrfSecret);

    // Used for backend calls with bearer token
    WebClient webClient = WebClient.create(vertx);

    router.route().handler(ctx -> {
      // Ensures that the csrf token request parameter is available for the CsrfHandler
      // after the logout form was submitted.
      ctx.request().setExpectMultipart(true);
      ctx.request().endHandler(v -> csrfHandler.handle(ctx));
    });

    String hostname = System.getProperty("http.post", "127.0.0.1");
    int port = Integer.getInteger("http.port", 8090);
    String baseUrl = String.format("http://%s:%d", hostname, port);
    String oauthCallbackPath = "/callback";

    OAuth2Options clientOptions = new OAuth2Options()
      .setFlow(OAuth2FlowType.AUTH_CODE)
      .setSite(System.getProperty("oauth2.issuer", "http://localhost:8080/auth/realms/earth_realm"))
      .setClientId(System.getProperty("oauth.client_id", "test_client_1"))
      .setClientSecret(System.getProperty("oauth2.client_secret", "3ebe77ad-17f0-4702-91a4-861e2ee94d3c"));

    KeycloakAuth.discover(vertx, clientOptions, asyncResult -> {
      OAuth2Auth oAuth2Auth = asyncResult.result();
      if (oAuth2Auth == null) {
        throw new RuntimeException("Could not configure Keycloak integration via OpenID Connect Discovery Endpoint. Is Keycloak running?");
      }

      OAuth2AuthHandler oauth2 = OAuth2AuthHandler.create(vertx, oAuth2Auth, baseUrl + oauthCallbackPath)
        .setupCallback(router.get(oauthCallbackPath));

      // protect resources beneath /protected/* with oauth2 handler
      router.route("/protected/*").handler(oauth2);

      // configure route handlers
//      configureRoutes(router, webClient, oAuth2Auth);
    });

    vertx.createHttpServer().requestHandler(router).listen(port, http -> {
      if (http.succeeded()) {
        startPromise.complete();
        System.out.println("HTTP server started on port " + port);
      } else {
        startPromise.fail(http.cause());
      }
    });
  }

  public static void main(String[] args) {
    Vertx vertx = Vertx.vertx();
    vertx.deployVerticle(new MainVerticle());
  }
}
