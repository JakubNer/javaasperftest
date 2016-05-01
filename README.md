# What is this?

Maven project to generate a WAR with a bunch of endpoint to excercise some architectural approaches on an application server.

# YouTube Videos

## producer/consumer

# Haystack JARs (Demo 1)

## Configuration

Kick off queueing 'sticks of hay' (lines of text) onto a farily hardcoded message queue.

JMS queue reads/writes using netty for Hornet:

    192.168.0.119;port=5445

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
  
## Demo Storyboard (producer/consumer)

This is the story board for the producer/consumer video.

    // @ no shiny text with no entry sign
    
    // @ title showing github code (Distributed producer/consumer solely with WildFly 9)
    
    This demo shows a solution to a needle in a haystack problem.  A "haystack" of many text phrases
    contains several phrases that are "needles" to be found.  The needles are to be found are provided as
    bcrypt hashes.
    
    The solution has one producer throwing batches of phrases--the hay--along with all the hashed needles,
    onto a queue.  A multitude of worker consumers pulls each batche off the queue and tries to find the needles
    in the haystack.  Found matches are reported back to the producer.
    
    This is a simple, elegant, pragmatic solution for a shop already using WildFly.  No unnecessary tooling complexity added.  
    
    // @ five ssh sessions
    
    For this demo I'm using five WildFly servers each in its own Virtual Machine, all on somewhat low powered personal PCs as hosts.  
   
    I've connected to all of them via PuTTY SSH.
    
    This solution is easily deployed and scaled out using nothing else than JBoss: WildFly 9 in this case.  Beyond what I've
    done here, the workers can be easily enough thrown into the cloud on top of any IaaS provider.
    
    // @ JMS console, zoom IP
    
    The producer instance also hosts HornetQ for this setup.  We're just using HornetQ as it comes out of the box shipped with WildFly.
    
    I will be coming back to this WildFly JMS console as we go along.
    
    // @ zoom red putty
    
    The red session will be where I start my producer.  
    
    // @ zoom out
    
    All the others will be consumers.
    
    // @ zoom red ls /opt/../deployments
    
    The producer and the consumers are deployed via simple JARs.  As soon as the producer JAR is deployed it starts producing
    the haystack and needles onto the queue.

    // @ intellij pom.xml
    
    The producer and consumer are simple Maven projects packeged into their separate jars.

    // @ intellij w/ CDI annotations for Singleton etc.
    
    Java EE CDI makes sure the producer starts working as soon as deployed.  
    
    // @ intellij message beans in consumer
    
    Java EE JMS annotations in the consumers ensure they're eager to work as soon as deployed.
    
    // @ intellij message bean in producer
    
    The producer also registers for the "backchannel" messages to find out when needles are found by the consumers.
    
    // demo with small number of files
    
    // no scaling needed?  do it on single instance: red
    
    // scaling needed, distribute.


