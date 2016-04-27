package template.rest

import javax.ejb.{ActivationConfigProperty, MessageDriven}
import javax.jms.{QueueSender, QueueSession, Session, Queue, ObjectMessage, QueueConnection, QueueConnectionFactory, MessageListener}
import javax.naming.{Context, InitialContext}
import java.util.Properties
import template.bcrypt.BCrypt

@MessageDriven(
  activationConfig = Array[ActivationConfigProperty](
    new ActivationConfigProperty(propertyName = "destination", propertyValue = "java:jboss/exported/jms/queues/HaystackQueue"),
    new ActivationConfigProperty(propertyName = "maxSession", propertyValue = "2"),
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
    if (msg.line.length == 0) {
      println("DONE MESSAGE")
      backchannelSend(msg.line)
    } else if (BCrypt.checkpw(msg.line, msg.hash)) {
      println("MATCH MESSAGE %s :: %s".format(msg.line, msg.hash))
      backchannelSend(msg.line)
    } else {
      println("NON MATCH MESSAGE %s :: %s".format(msg.line, msg.hash))
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
    val queue: Queue = ic.lookup("java:/jms/queues/HaystackBackchannelQueue").asInstanceOf[Queue]
    val factory: QueueConnectionFactory = ic.lookup("java:/jms/RemoteConnectionFactory").asInstanceOf[QueueConnectionFactory]
    var connection: QueueConnection = factory.createQueueConnection("jms","jms")
    var session: QueueSession = connection.createQueueSession(false, Session.AUTO_ACKNOWLEDGE)
    var sender: QueueSender = session.createSender(queue);
    var msg: BackchannelMessage = new BackchannelMessage(lineFound)
    var oMsg: ObjectMessage = session.createObjectMessage(msg)
    sender.send(oMsg)
    session.close
    connection.close
  }
}