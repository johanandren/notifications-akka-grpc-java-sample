## Description

A small client-server example for Akka gRPC allowing posting of "notifications" to different recipient id:s through a minimal web UI and then over gRPC to an Akka server 
and showcasing streaming notifications for such receiever ids, again over gRPC and then websocket to a browser.

If there is no subscribers active for a given id the notifications are buffered until there is a subscriber

To run:

* start the server/service `notifications-service-akka` with `mvn initialize exec:exec` (this can likely not be done from IDE because of java-agent parameter not being picked up)
* start the webserver `notifications-ui-akka` with `mvn exec:java`
* post notifications from http://127.0.0.1/
* get notifications at http://127.0.0.1/view/[n] where n is the recipient id

## Prerequisites:

 * Java
 * Maven
 * A locally published version of Akka gRPC 
