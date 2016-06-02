package template.consumer

import javax.ejb.{ActivationConfigProperty, MessageDriven}
import javax.jms._
import java.util.{Properties,Map,HashMap}

import template.bcrypt.BCrypt
import template.messaging._
import org.hornetq.core.remoting.impl.netty.NettyConnectorFactory
import org.hornetq.api.core.TransportConfiguration
import org.hornetq.api.jms.{HornetQJMSClient, JMSFactoryType}

@MessageDriven(
  activationConfig = Array[ActivationConfigProperty](
    new ActivationConfigProperty(propertyName = "destination", propertyValue = "java:jboss/exported/jms/queues/HaystackQueue"),
    new ActivationConfigProperty(propertyName = "maxSession", propertyValue = "3"),
    new ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
    new ActivationConfigProperty(propertyName = "acknowledgeMode", propertyValue = "Auto-acknowledge"),
    new ActivationConfigProperty(propertyName="user", propertyValue = "jms"),
    new ActivationConfigProperty(propertyName="password", propertyValue = "jms"),
    new ActivationConfigProperty(propertyName="connectorClassName", propertyValue = "org.hornetq.core.remoting.impl.netty.NettyConnectorFactory"),
    new ActivationConfigProperty(propertyName="connectionParameters", propertyValue = "host=192.168.0.118;port=5445")),
  messageListenerInterface = classOf[MessageListener]
)
class NeedleFinder extends MessageListener {

  @Override
  def onMessage(message:javax.jms.Message): Unit = {
    val msg: NeedleCandidateMessage = message.asInstanceOf[ObjectMessage].getObject().asInstanceOf[NeedleCandidateMessage]
    println("PROCESSING MESSAGE %d :: %d/%d".format(msg.msgid, msg.lines.size, msg.hashes.size))
    for (hash <- msg.hashes) {

      for (line <- msg.lines) {
        if (line != null && BCrypt.checkpw(line, hash)) {
          println("MATCH MESSAGE %s :: %s".format(line, hash))
          backchannelSend(line,hash)
        }
      }
    }
  }

  def backchannelSend(lineFound:String,hashFound:String): Unit = {
    println("backchannel %s".format(lineFound))
    var props : Properties = new Properties();

    var connectionParams : Map[String,Object] = new HashMap[String, Object]
    connectionParams.put(org.hornetq.core.remoting.impl.netty.TransportConstants.PORT_PROP_NAME,"5445")
    connectionParams.put(org.hornetq.core.remoting.impl.netty.TransportConstants.HOST_PROP_NAME,"192.168.0.118")
    var transportConfiguration: TransportConfiguration = new TransportConfiguration(classOf[NettyConnectorFactory].getName,connectionParams)

    val factory: ConnectionFactory = HornetQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF,transportConfiguration)
    var connection: Connection = factory.createConnection("jms","jms")
    var session: QueueSession = connection.createSession(false, Session.AUTO_ACKNOWLEDGE).asInstanceOf[QueueSession]
    val queue: Queue = HornetQJMSClient.createQueue("HaystackBackchannelQueue")
    var sender: QueueSender = session.createSender(queue);
    var msg: BackchannelMessage = new BackchannelMessage(lineFound,hashFound)
    var oMsg: ObjectMessage = session.createObjectMessage(msg)
    sender.send(oMsg)
    session.close
    connection.close
    println("backchannel sent %s".format(lineFound))
  }
}
