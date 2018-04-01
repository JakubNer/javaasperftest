# What is this?

Are you using an Application Server (AS)--such as JBoss/WildFly--that has modules coupled to other layered modules running on the same AS?  If you've tried to make microservices out of a monolith it's quite possible you've got code that does exactly this.

This Maven project generates a WAR with a bunch of endpoints to excercise some architectural approaches of the above.

This module is a demo/teaching tool as to how resources are possibly wasted.  It has endpoints to enable comparisson of alternate architectures:

* synchronous loopback callbacks (problem, runs out of request threads)
* asynchronous loopback callbacks (problem, runs out of client connections unless async APIs shared among modules)
* akka (nice but needs to be pervasive)
* queueing (nice, maybe use Apache Camel?)

The purpose of this is to convey how the application server interacts between request connections/threads, client connections (outgoing connections)

# Haystack JARs (Demo 1)

## Configuration

Kick off queueing 'sticks of hay' (lines of text) onto a farily hardcoded message queue.

JMS queue reads/writes using netty for Hornet:

    192.168.0.118;port=5445

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
              <jms-connection-factories>
                  ...
                  <connection-factory name="RemoteConnectionFactory">
                       ...
                       <consumer-window-size>0</consumer-window-size>
                  </connection-factory>
                  ...
              <jms-connection-factories>
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

# Demo Endpoints (Demo 2)

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
  

