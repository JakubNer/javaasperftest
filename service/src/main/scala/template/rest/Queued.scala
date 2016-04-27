package template.rest

import java.util.concurrent.TimeUnit
import javax.ws.rs.container.{AsyncResponse, Suspended}
import javax.ws.rs.core.{Context, Response, UriInfo}
import javax.ws.rs.{GET, Path, Produces, QueryParam}
import javax.ejb.{MessageDriven,ActivationConfigProperty}
import javax.jms.{MessageListener,ObjectMessage,Queue,QueueConnection,QueueConnectionFactory,QueueSender,Session,QueueSession}
import javax.naming.{InitialContext}

@Path("queued")
class Queued {
  @GET
  @Produces(Array("text/plain"))
  def async(@Context uriInfo: UriInfo,
            @QueryParam("conns") conns: Int,
            @QueryParam("iter") iter: Int,
            @Suspended response: AsyncResponse) = {
    response.setTimeout(20l,TimeUnit.MINUTES)
    Stats.start
    println("setting up backchannel")
    QueueSupervisor.start(response)
    println("starting queue calls")
    QueueSupervisor.send(conns, conns, iter, true)
  }
}

@SerialVersionUID(100L)
class QueueMessage (val connsTotal:Int, val conn:Int, val iter:Int, val isLastConnectionInPreviousIteration: Boolean) extends Serializable

object QueueSupervisor {
  private var response: AsyncResponse = null

  def start(response: AsyncResponse) = {
    println("backchannel waiting")
    this.response = response
  }

  def send(connsTotal:Int,conn:Int, iter:Int, isLastConnectionInPreviousIteration: Boolean): Unit = {
    var queue: Queue = InitialContext.doLookup("/queue/TestQueue");
    var factory: QueueConnectionFactory = InitialContext.doLookup("java:/ConnectionFactory");
    var connection: QueueConnection =  factory.createQueueConnection();
    var session: QueueSession = connection.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);
    var sender: QueueSender = session.createSender(queue);
    var msg: QueueMessage = new QueueMessage(connsTotal, conn, iter, isLastConnectionInPreviousIteration)
    var oMsg: ObjectMessage = session.createObjectMessage(msg)
    sender.send(oMsg)
    session.close
    connection.close
  }

  def done =  {
    println("backchannel pinged")
    Stats.done
    response.resume(Response.status(200).entity("OK %s".format(Stats.getDumpageStr())).build)
  }
}

@MessageDriven(
  activationConfig = Array[ActivationConfigProperty](
    new ActivationConfigProperty(propertyName = "destination", propertyValue = "/queue/TestQueue"),
    new ActivationConfigProperty(propertyName = "maxSession", propertyValue = "1"),
    new ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
    new ActivationConfigProperty(propertyName = "acknowledgeMode", propertyValue = "Auto-acknowledge")),
  messageListenerInterface = classOf[MessageListener]
)
class QueueWorker extends MessageListener {
  @Override
  def onMessage(message:javax.jms.Message): Unit = {
    val msg: QueueMessage = message.asInstanceOf[ObjectMessage].getObject().asInstanceOf[QueueMessage]
    if (msg.connsTotal == 0) {
      println("notify backchannel from queue")
      QueueSupervisor.done
    } else {
      Stats.requesting
      if (msg.iter == 0) {
        // end of recursion for connections, leaf
        Thread.sleep(Configs.leafDelayMillis) // time for business logic per library
      } else {
        Thread.sleep(Configs.bodyDelayMillis) // time for business logic per library
      }
      Stats.replied
      if (msg.iter == 0) {
        if (msg.isLastConnectionInPreviousIteration) {
          QueueSupervisor.send(0, 0, 0, true)
        }
      } else {
        for (c <- 1 to msg.connsTotal) {
          QueueSupervisor.send(msg.connsTotal, c, msg.iter-1, msg.isLastConnectionInPreviousIteration && c == msg.connsTotal)
        }
      }
    }
  }
}
