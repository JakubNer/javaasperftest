# What is this?

Maven project to generate a WAR with a bunch of endpoint to excercise some architectural approaches on an application server.

# YouTube Videos

## producer/consumer



# Endpoints

## http://localhost:8080/limits/rest/haystack

Kick off queueing 'sticks of hay' (lines of text) onto a farily hardcoded message queue.

JMS queue reads from:

    192.168.0.119;port=5445

JMS queue writes to:

    http-remoting://192.168.0.119:8080
  
Sample Wildfly standalone.xml:

          <subsystem xmlns="urn:jboss:domain:messaging:3.0">
            <hornetq-server>
              ...
              <paging-directory path="/paging"/>
              ...
              <address-settings>
                  <address-setting match="#">
                      <max-size-bytes>104857600</max-size-bytes>
                      <page-size-bytes>10485760</page-size-bytes>
                      <address-full-policy>BLOCK</address-full-policy>
                  </address-setting>
              </address-settings>
              ...
              <jms-destinations>
                ...
                <jms-queue name="HaystackQueue">
                    <entry name="/queue/HaystackQueue"/>
                    <entry name="java:jboss/exported/jms/queues/HaystackQueue"/>
                </jms-queue>
                <jms-queue name="HaystackBackchannelQueue">
                    <entry name="/queue/HaystackBackchannelQueue"/>
                    <entry name="java:jboss/exported/jms/queues/HaystackBackchannelQueue"/>
                </jms-queue>
              </jms-destinations>
              ...
            </hornetq-server>
         </subsystem>
          ...
          <socket-binding-group name="standard-sockets" default-interface="public" port-offset="${jboss.socket.binding.port-offset:0}">
            ...
            <socket-binding name="messaging" port="5445"/>
            ...
          </socket-binding-group>
          
Also make sure the following user is configured for 'guest' role:

    User: jms
    Password: jms

## localhost:8080\limits\rest\config?bodyDelayMillis=0&leafDelayMillis=100&numAsyncRequests=3&numWorkers=3&useThreadForAsync=true

Configuration for endpoints below.

* bodyDelayMillis
  * millis to Thread.sleep on each recusrive call into 'sync'/'async'/'queued' that is not a leaf/end call of tree of calls
* leafDelayMillis
  * millis to Thread.sleep on each recusrive call into 'sync'/'async'/'queued' that is a leaf/end call of tree of calls
* numAsyncRequests
  * number of threads to use for JAX-RS client calling async HTTP ('async' endpoint)
* numWorkers
  * number of worker threads for 'queued' endpoint
* useThreadForAsync
  * for 'async' endpoint should the request thread be used to make async calls [false] or should a separate thread be used [true]

## http://localhost:8080/limits/rest/sync?conns=2&iter=2

Each recusive connection is a synchronous HTTP call using JAX-RS: starting from request thread.

* conns
  * # connections to children (down tree) to make for each connection not a leaf
* iter
  * # iterations to recurse (e.g. '1' means 'conns' recursive leaf calls made).

## http://localhost:8080/limits/rest/async?conns=2&iter=2

Each recursive connection is an asynchronous HTTP call using JAX-RS.

* conns
  * see 'sync' above
* iter
  * see 'sync' above

## http://localhost:8080/limits/rest/queued?conns=2&iter=2

Each recursion is handled through a queue configured in the WAR.

* conns
  * see 'sync' above
* iter
  * see 'sync' above

## http://localhost:8080/limits/rest/akka?conns=2&iter=2

Like 'queued' except Akka is used with a nicer backchannel.
           
* conns
  * see 'sync' above
* iter
  * see 'sync' above
  
## Demo Storyboard (producer/consumer)

This is the story board for the producer/consumer video.

    // @ no shiny text with no entry sign
    
    This solution is easily coded up using standard technologies.
    
    This solution is easily deployed and scaled out using nothing else than JBoss, WildFly in this case.
    
    // @ four ssh sessions
    
    We're using four WildFly servers running in Virtual Machines on somewhat low powered personal PCs.  
    
    I've connected to all of them via PuTTY SSH
    
    // @ zoom red putty
    
    The red one will be where I start my producer.  
    
    // @ zoom out
    
    The others will be consumers.
    
    // file system deployment of simple jars leveraging CDI
    
    // demo with small number of files
    
    // no scaling needed?  do it on single instance: red
    
    // scaling needed, distribute.


