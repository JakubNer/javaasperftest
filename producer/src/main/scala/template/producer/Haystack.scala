package template.producer

import javax.ejb.{ActivationConfigProperty, MessageDriven, Singleton, Startup}
import javax.jms.{MessageConsumer, MessageListener, ObjectMessage, Queue, QueueConnection, QueueConnectionFactory, QueueSender, QueueSession, Session}
import javax.naming.{InitialContext}
import java.io.InputStream
import java.util.{Date}
import javax.annotation.PostConstruct

import scala.collection.mutable.ListBuffer
import template.messaging._

@Startup
@Singleton
class Haystack {

  private val queue: Queue = InitialContext.doLookup("/queue/HaystackQueue");
  private val factory: QueueConnectionFactory = InitialContext.doLookup("java:/ConnectionFactory");
  private var connection: QueueConnection = null
  private var session: QueueSession = null
  private var sender: QueueSender = null
  private var consumer: MessageConsumer = null

  private val NUM_LINES_PER_MESSAGE = 30000

  @PostConstruct
  def haystack(): Unit = {
    new Thread {
      override def run = {
        queueOpen
        queueClear
        Tracker.start
        Tracker.hashes = getLinesOfText("/hashes2.txt").toArray
        val lines = getLinesOfText("/haystack2.txt")
        var msgid = 0
        for (hash <- Tracker.hashes) {
          var it = lines.iterator
          while (it.hasNext) {
            msgid += 1
            var lines: Array[String] = Array.fill[String](NUM_LINES_PER_MESSAGE)(null)
            it.copyToArray(lines, 0, NUM_LINES_PER_MESSAGE)
            queueUp(msgid, lines, Array(hash))
          }
          Tracker.waitTillFound(hash)
          queueClear
        }
        queueClose
        Tracker.end
      }
    }.start
  }

  def queueOpen = {
    connection = factory.createQueueConnection();
    session = connection.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);
    sender = session.createSender(queue);
    consumer = session.createConsumer(queue);
  }

  def queueClose = {
    session.close
    connection.close
  }

  def queueClear = {
    println("queueClear start")
    connection.start
    var num : Int = 0
    while (consumer.receiveNoWait() != null) {num += 1}
    connection.stop
    println("queueClear cleared %d messages".format(num))
  }

  def queueUp(msgid: Int, lines: Array[String], hashes:Array[String]): Unit = {
    if (msgid == -1) println("queueUp %d :: DONE".format(msgid))
    else println("queueUp %d :: %d/%d".format(msgid, lines.length,hashes.length))
    var msg: NeedleCandidateMessage = new NeedleCandidateMessage(msgid, lines, hashes)
    var oMsg: ObjectMessage = session.createObjectMessage(msg)
    sender.send(oMsg)
  }

  def getLinesOfText(fileName:String): List[String] = {
    val stream : InputStream = getClass.getResourceAsStream(fileName)
    return scala.io.Source.fromInputStream(stream).getLines.toList
  }
}

object Tracker {
  private val date: java.text.SimpleDateFormat = new java.text.SimpleDateFormat("HH:mm:ss:SSS")

  var hashes: Array[String] = null
  var foundLine: ListBuffer[String] = ListBuffer[String]()
  var foundHash: ListBuffer[String] = ListBuffer[String]()
  var start_time: Long = 0L
  var end_time: Long = 0L

  def start = {
    hashes = null
    foundLine = ListBuffer[String]()
    foundHash = ListBuffer[String]()
    start_time = System.currentTimeMillis()
    println("START %s".format(date.format((new Date))))
  }

  def end = {
    end_time = System.currentTimeMillis()
    println("END %s".format(date.format((new Date))))
    foundLine.map(str => println("IN END FOUND : '%s'".format(str)))
    if (foundLine.size == hashes.length) {
      println("Found all %d needles in %d ms".format(foundLine.length, end_time - start_time))
    } else {
      println("something went wrong!...did not find all %d needles! (Only %d)".format(hashes.length,foundLine.size))
    }
  }

  def doFind (line:String,hash:String) : Unit = {
    println("FOUND : '%s' for '%s'".format(line,hash))
    Tracker.foundLine +=line
    Tracker.foundHash.synchronized {
      Tracker.foundHash += hash
      Tracker.foundHash.notifyAll
    }
  }

  def waitTillFound(hash:String) : Unit = {
    Tracker.foundHash.synchronized {
      while (! Tracker.foundHash.contains(hash)) {
        println("waitTillFound: waiting... '%s'".format(hash))
        Tracker.foundHash.wait
      }
      println("waitTillFound: found... '%s'".format(hash))
    }
  }
}

@MessageDriven(
  activationConfig = Array[ActivationConfigProperty](
    new ActivationConfigProperty(propertyName = "destination", propertyValue = "java:jboss/exported/jms/queues/HaystackBackchannelQueue"),
    new ActivationConfigProperty(propertyName = "maxSession", propertyValue = "1"),
    new ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
    new ActivationConfigProperty(propertyName = "acknowledgeMode", propertyValue = "Auto-acknowledge"),
    new ActivationConfigProperty(propertyName="user", propertyValue = "jms"),
    new ActivationConfigProperty(propertyName="password", propertyValue = "jms"),
    new ActivationConfigProperty(propertyName="connectorClassName", propertyValue = "org.hornetq.core.remoting.impl.netty.NettyConnectorFactory"),
    new ActivationConfigProperty(propertyName="connectionParameters", propertyValue = "host=192.168.0.118;port=5445")),
  messageListenerInterface = classOf[MessageListener]
)
class HaystackBackchannelWorker extends MessageListener {
  @Override
  def onMessage(message:javax.jms.Message): Unit = {
    val msg: BackchannelMessage = message.asInstanceOf[ObjectMessage].getObject().asInstanceOf[BackchannelMessage]
    Tracker.doFind(msg.foundLine, msg.foundHash)
  }
}


