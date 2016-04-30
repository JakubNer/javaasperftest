package template.producer

import javax.ejb.{ActivationConfigProperty, MessageDriven}
import javax.jms.{MessageListener, ObjectMessage, Queue, QueueConnection, QueueConnectionFactory, QueueSender, QueueSession, Session}
import javax.naming.InitialContext
import java.io.InputStream
import java.util.Date
import scala.collection.mutable.ListBuffer
import template.messaging._


object Haystack {
  private val queue: Queue = InitialContext.doLookup("/queue/HaystackQueue");
  private val factory: QueueConnectionFactory = InitialContext.doLookup("java:/ConnectionFactory");
  private var connection: QueueConnection = null
  private var session: QueueSession = null
  private var sender: QueueSender = null

  private val NUM_LINES_PER_MESSAGE = 3

  haystack()

  def haystack(): Unit = {
    queueOpen
    Tracker.start
    Tracker.hashes = getLinesOfText("/hashes2.txt").toArray
    var it = getLinesOfText("/haystack2.txt")
    var msgid = 0
    while (it.hasNext) {
      msgid += 1
      var lines: Array[String] = Array.fill[String](NUM_LINES_PER_MESSAGE)(null)
      it.copyToArray(lines, 0, NUM_LINES_PER_MESSAGE)
      queueUp(msgid, lines, Tracker.hashes)
    }
    queueUp(-1,null,null)
    queueClose
  }

  def queueOpen = {
    connection = factory.createQueueConnection();
    session = connection.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);
    sender = session.createSender(queue);
  }

  def queueClose = {
    session.close
    connection.close
  }

  def queueUp(msgid: Int, lines: Array[String], hashes:Array[String]): Unit = {
    if (msgid == -1) println("queueUp %d :: DONE".format(msgid))
    else println("queueUp %d :: %d/%d".format(msgid, lines.length,hashes.length))
    var msg: NeedleCandidateMessage = new NeedleCandidateMessage(msgid, lines, hashes)
    var oMsg: ObjectMessage = session.createObjectMessage(msg)
    sender.send(oMsg)
  }

  def getLinesOfText(fileName:String): Iterator[String] = {
    val stream : InputStream = getClass.getResourceAsStream(fileName)
    return scala.io.Source.fromInputStream(stream).getLines
  }
}

object Tracker {
  private val date: java.text.SimpleDateFormat = new java.text.SimpleDateFormat("HH:mm:ss:SSS")

  var hashes: Array[String] = null
  var found: ListBuffer[String] = ListBuffer[String]()
  var start_time: Long = 0L
  var end_time: Long = 0L

  def start = {
    hashes = null
    found = ListBuffer[String]()
    start_time = System.currentTimeMillis()
    println("START %s".format(date.format((new Date))))
  }

  def end = {
    end_time = System.currentTimeMillis()
    println("END %s".format(date.format((new Date))))
    found.map(str => println("IN END FOUND : '%s'".format(str)))
    if (found.size == hashes.length) {
      println("Found all %d needles in %d ms".format(found.length, end_time - start_time))
    } else {
      println("something went wrong!...did not find all %d needles! (Only %d)".format(hashes.length,found.size))
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
    new ActivationConfigProperty(propertyName="connectionParameters", propertyValue = "host=192.168.0.119;port=5445")),
  messageListenerInterface = classOf[MessageListener]
)
class HaystackBackchannelWorker extends MessageListener {
  @Override
  def onMessage(message:javax.jms.Message): Unit = {
    val msg: BackchannelMessage = message.asInstanceOf[ObjectMessage].getObject().asInstanceOf[BackchannelMessage]
    if (msg.foundLine == null) {
      Tracker.end
    } else {
      println("FOUND : '%s'".format(msg.foundLine))
      Tracker.found += msg.foundLine
    }
  }
}


