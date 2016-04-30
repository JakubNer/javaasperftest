package template.consumer

import javax.ejb.{ActivationConfigProperty, MessageDriven}
import javax.jms.{QueueSender, QueueSession, Session, Queue, ObjectMessage, QueueConnection, QueueConnectionFactory, MessageListener}
import javax.naming.{Context, InitialContext}
import java.util.Properties
import template.bcrypt.BCrypt
import template.messaging._

@MessageDriven(
  activationConfig = Array[ActivationConfigProperty](
    new ActivationConfigProperty(propertyName = "destination", propertyValue = "java:jboss/exported/jms/queues/HaystackQueue"),
    new ActivationConfigProperty(propertyName = "maxSession", propertyValue = "3"),
    new ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
    new ActivationConfigProperty(propertyName = "acknowledgeMode", propertyValue = "Auto-acknowledge"),
    new ActivationConfigProperty(propertyName="user", propertyValue = "jms"),
    new ActivationConfigProperty(propertyName="password", propertyValue = "jms"),
    new ActivationConfigProperty(propertyName="connectorClassName", propertyValue = "org.hornetq.core.remoting.impl.netty.NettyConnectorFactory"),
    new ActivationConfigProperty(propertyName="connectionParameters", propertyValue = "host=192.168.0.119;port=5445")),
  messageListenerInterface = classOf[MessageListener]
)
class NeedleFinder extends MessageListener {

  @Override
  def onMessage(message:javax.jms.Message): Unit = {
    val msg: NeedleCandidateMessage = message.asInstanceOf[ObjectMessage].getObject().asInstanceOf[NeedleCandidateMessage]
    if (msg.msgid == -1) {
      println("DONE MESSAGE")
      backchannelSend(null)
    } else {
      println("PROCESSING MESSAGE %d :: %d/%d".format(msg.msgid, msg.lines.size, msg.hashes.size))
      for (hash <- msg.hashes) {
        for (line <- msg.lines) {
          if (line != null && BCrypt.checkpw(line, hash)) {
            println("MATCH MESSAGE %s :: %s".format(line, hash))
            backchannelSend(line)
          }
        }
      }
    }
  }

  def backchannelSend(lineFound:String): Unit = {
    println("backchannel %s".format(lineFound))
    var props : Properties = new Properties();
    props.put(Context.INITIAL_CONTEXT_FACTORY,"org.jboss.naming.remote.client.InitialContextFactory");
    props.put(Context.PROVIDER_URL, "http-remoting://192.168.0.119:8080");
    props.put(Context.SECURITY_PRINCIPAL, "jms");
    props.put(Context.SECURITY_CREDENTIALS, "jms");
    val ic: InitialContext = new InitialContext(props);
    val factory: QueueConnectionFactory = ic.lookup("jms/RemoteConnectionFactory").asInstanceOf[QueueConnectionFactory]
    var connection: QueueConnection = factory.createQueueConnection("jms","jms")
    var session: QueueSession = connection.createQueueSession(false, Session.AUTO_ACKNOWLEDGE)
    val queue: Queue = ic.lookup("jms/queues/HaystackBackchannelQueue").asInstanceOf[Queue]
    var sender: QueueSender = session.createSender(queue);
    var msg: BackchannelMessage = new BackchannelMessage(lineFound)
    var oMsg: ObjectMessage = session.createObjectMessage(msg)
    sender.send(oMsg)
    session.close
    connection.close
    println("backchannel sent %s".format(lineFound))
  }
}
