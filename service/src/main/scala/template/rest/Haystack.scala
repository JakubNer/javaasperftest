package template.rest

import javax.ws.rs.core.Response
import javax.ws.rs.{GET, Path, Produces}
import javax.ejb.{ActivationConfigProperty, MessageDriven}
import javax.jms.{MessageListener, ObjectMessage, Queue, QueueConnection, QueueConnectionFactory, QueueSender, QueueSession, Session}
import javax.naming.InitialContext
import java.io.InputStream
import java.util.Date
import scala.collection.mutable.ListBuffer

@Path("haystack")
class Haystack {
  private val queue: Queue = InitialContext.doLookup("/queue/HaystackQueue");
  private val factory: QueueConnectionFactory = InitialContext.doLookup("java:/ConnectionFactory");
  private var connection: QueueConnection = null
  private var session: QueueSession = null
  private var sender: QueueSender = null

  @GET
  @Produces(Array("text/plain"))
  def haystack(): Response = {
    queueOpen
    Tracker.start
    for(hash <- getLinesOfText("/hashes2.txt")) {
      Tracker.expected += 1
      for (line <- getLinesOfText("/haystack2.txt"))
        queueUp(line, hash)
    }
    queueUp("","")
    queueClose
    return Response.status(200).entity("OK %s".format(Stats.getDumpageStr())).build
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

  def queueUp(line: String, hash:String): Unit = {
    println("queueUp %s/%s".format(line,hash))
    var msg: NeedleCandidateMessage = new NeedleCandidateMessage(line, hash)
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

  var expected: Int = 0
  var found: ListBuffer[String] = ListBuffer[String]()
  var start_time: Long = 0L
  var end_time: Long = 0L

  def start = {
    expected = 0
    found = ListBuffer[String]()
    start_time = System.currentTimeMillis()
    println("START %s".format(date.format((new Date))))
  }

  def end = {
    end_time = System.currentTimeMillis()
    println("END %s".format(date.format((new Date))))
    if (found.length == expected) {
      println("Found all %d needles in %d ms".format(found.length, end_time - start_time))
      found.map(str => println("FOUND : '%s'".format(str)))
    } else {
      println("something went wrong!...did not find all %d needles!".format(expected))
      found.map(str => println("FOUND : '%s'".format(str)))
    }
  }
}

@SerialVersionUID(100L)
class NeedleCandidateMessage (val line: String, val hash:String) extends Serializable

@SerialVersionUID(101L)
class BackchannelMessage (val foundLine: String) extends Serializable

@MessageDriven(
  activationConfig = Array[ActivationConfigProperty](
    new ActivationConfigProperty(propertyName = "destination", propertyValue = "/queue/HaystackBackchannelQueue"),
    new ActivationConfigProperty(propertyName = "maxSession", propertyValue = "1"),
    new ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
    new ActivationConfigProperty(propertyName = "acknowledgeMode", propertyValue = "Auto-acknowledge")),
  messageListenerInterface = classOf[MessageListener]
)
class HaystackBackchannelWorker extends MessageListener {
  @Override
  def onMessage(message:javax.jms.Message): Unit = {
    val msg: BackchannelMessage = message.asInstanceOf[ObjectMessage].getObject().asInstanceOf[BackchannelMessage]
    if (msg.foundLine.length() > 0) {
      println("FOUND : '%s'".format(msg.foundLine))
      Tracker.found += msg.foundLine
    } else {
      Tracker.end
    }
  }
}


