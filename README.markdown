## spray-client concurrent usage example

a spray application running on spray-can, using spray-json to [un]marshal things and spray-client to reach the web and the couchdb. To run it:

1. Install couchdb using your package manager which will sit on http://localhost:5984/ by default.
2. Go to http://localhost:5984/\_utils and create a database named "db"
3. clone the application, run re:start in sbt shell

To make it collect/save things, you should send some urls to its "/load" path. in order to do that.

1. Create a file with urls in lines (f.e. urls.txt)
2. Send this to application with

        $ curl --data-binary @urls.txt http://localhost:8080/load
        (--data-binary is required because curl seems to strip newlines with -d)

Service will send each url to actor "Web", which will create an HttpConduit for the host, request the path and on completion, send the content to actor "Db", finally closing the conduit. "Db" will ask couch if it already has a record with the given url. According to response, "Db" will update or create the record. After that, it will split the content to its lines and sends messages to itself with each line as a new content. Which in turn would make itself ask couch for those lines and create/update accordingly. Pseudo http conversation should go like that for a single URL.

    GET      http://localhost:8080/path (with content) http://google.com/
    GET      http://google.com/
    ...      (after response from google, close HttpConduit)
    GET      http://localhost:5984/db/id_for_google.com
    ...      (after response from couch, don't close anything)
    PUT      http://localhost:5984/db/id_for_google.com (with content) <html> \n google.com \n </html>
    GET      http://localhost:5984/db/id_for_google.com@1
    PUT      http://localhost:5984/db/id_for_google.com@1 (with content) <html>
    GET      http://localhost:5984/db/id_for_google.com@2
    PUT      http://localhost:5984/db/id_for_google.com@2 (with content) google.com
    GET      http://localhost:5984/db/id_for_google.com@3
    PUT      http://localhost:5984/db/id_for_google.com@3 (with content) </html>

Since this is a concurrent application, errors are not deterministic, they sometimes show up, sometimes they don't. The thing that get me into everything is this:

    app: 01/07 13:10:28 ERROR[akka:event-driven:dispatcher:event:handler-19] a.e.s.Slf4jEventHandler - 
    app:   [akka.dispatch.DefaultCompletableFuture]
    app: 	[]
    app: 	[java.lang.NullPointerException
    app: 	at cc.spray.client.HttpConduit$MainActor$Conn$$anonfun$dispatchTo$1$2.apply(HttpConduit.scala:108)
    app: 	at cc.spray.client.HttpConduit$MainActor$Conn$$anonfun$dispatchTo$1$2.apply(HttpConduit.scala:107)
    app: 	at akka.dispatch.DefaultCompletableFuture.akka$dispatch$DefaultCompletableFuture$$notifyCompleted(Future.scala:927)
    ...

line 108 of HttpConduit is a onComplete call which makes stack trace rather pointless. I've put tracing statements around the line and found that "self" is the thing that is null. Since that is nonsense, i traced further (by changing the debug line at HttpConduit:94 to also log the request with host:port) and found out the situation is even more serious:

    ...
    app: Opening new connection to www.infoq.com:80 to request PUT request to http://www.infoq.com:80/db/http%3A%2F%2Ftwitter.com%2F%4015
    app: Opening new connection to www.infoq.com:80 to request PUT request to http://www.infoq.com:80/db/http%3A%2F%2Ftwitter.com%2F%4016
    app: Opening new connection to blogs.reuters.com:80 to request PUT request to http://blogs.reuters.com:80/db/http%3A%2F%2Ftwitter.com%2F%4029
    app: Opening new connection to gapingvoid.com:80 to request PUT request to http://gapingvoid.com:80/db/http%3A%2F%2Ftwitter.com%2F%4032
    app: Opening new connection to www.infoq.com:80 to request PUT request to http://www.infoq.com:80/db/http%3A%2F%2Ftwitter.com%2F%4048
    app: Opening new connection to www.infoq.com:80 to request PUT request to http://www.infoq.com:80/db/http%3A%2F%2Ftwitter.com%2F%4060
    app: Opening new connection to www.infoq.com:80 to request PUT request to http://www.infoq.com:80/db/http%3A%2F%2Ftwitter.com%2F%4063
    ...

You can see the inconsistency here. "Db" actor tries to write the lines 15, 16, 29, 32, 48, 60 and 63 of http://twitter.com/ response to couch. But most of them goes to infoq.com, others to reuters and such. After those hosts tell me to 400 or 404 away, HttpConduit tries to handle that response; but sometimes self is null, because i probably have already closed it after getting the actual resource (at infoq, reuters and gapingvoid). If i haven't, the 400 response might be handled as the original response for the host, which will create even more problems without even telling. The problem is, sometimes my HttpConduit in "Db", gets the connection for another host (probably in progress at actor "Web") instead of localhost:5984 when reaching for one.