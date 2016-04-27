package template.rest

import java.util.concurrent.TimeUnit
import javax.ws.rs.container.{AsyncResponse, Suspended}
import javax.ws.rs.core.{Context, Response, UriInfo}
import javax.ws.rs.{GET, Path, Produces, QueryParam}

import akka.actor._
import akka.pattern.ask
import akka.routing.{ActorRefRoutee, RoundRobinRoutingLogic, Router}

import scala.concurrent._
import scala.concurrent.duration._
import ExecutionContext.Implicits.global
import scala.util.{Success, Failure}
import akka.util.{Timeout}

@Path("akka")
class Akka {
  @GET
  @Produces(Array("text/plain"))
  def async(@Context uriInfo: UriInfo,
            @QueryParam("conns") conns: Int,
            @QueryParam("iter") iter: Int,
            @Suspended response: AsyncResponse) = {
    response.setTimeout(20l,TimeUnit.MINUTES)
    Stats.start
    println("setting up backchannel")
    AkkaSupervisor.start(Configs.numWorkers)
    println("starting messaging")
    ask(AkkaSupervisor.supervisor, Message.JOB(conns, conns, iter, true))(Timeout(20 minutes)).mapTo[String] onComplete {
      case Success("DONE") =>
        println("backchannel pinged")
        Stats.done
        response.resume(Response.status(200).entity("OK %s".format(Stats.getDumpageStr())).build)
    }
  }
}

object Message {
  case class JOB(connsTotal: Int, var conn: Int, var iter: Int, isLastConnectionInPreviousIteration: Boolean)
}

class AkkaSupervisor(system: ActorSystem, workerRouter : Router) extends Actor {
  def receive =  {
    case job: Message.JOB =>
      workerRouter.route(job, sender())
  }
}

object AkkaSupervisor {
  private var system = ActorSystem("template")
  var supervisor : ActorRef = null

  private var response: AsyncResponse = null
  private var workerRouter : Router = null

  def start(numWorkers: Int) = {
    println("backchannel waiting")
    resetRouter(numWorkers)
    supervisor = system.actorOf(Props(classOf[AkkaSupervisor], system, workerRouter))
  }

  def resetRouter(numWorkers: Int) = {
    if (workerRouter != null) {
      for (routee <- workerRouter.routees) {
        workerRouter.removeRoutee(routee)
      }
    }
    workerRouter = {
      val routees = Vector.fill(numWorkers) {
        val r = system.actorOf(Props[AkkaWorker])
        ActorRefRoutee(r)
      }
      Router(RoundRobinRoutingLogic(), routees)
    }
  }
}

class AkkaWorker extends Actor {
  def receive = {
    case Message.JOB(connsTotal, conn, iter, isLastConnectionInPreviousIteration) =>
      val _sender = sender
      if (connsTotal == 0) {
        println("notify backchannel from queue")
        _sender ! "DONE"
      } else {
        Stats.requesting
        if (iter == 0) {
          // end of recursion for connections, leaf
          Thread.sleep(Configs.leafDelayMillis) // time for business logic per library
        } else {
          Thread.sleep(Configs.bodyDelayMillis) // time for business logic per library
        }
        Stats.replied
        if (iter == 0) {
          if (isLastConnectionInPreviousIteration) {
            ask(AkkaSupervisor.supervisor, Message.JOB(0, 0, 0, true))(Timeout(20 minutes)).mapTo[String] onComplete
            {
              case Success("DONE") => _sender ! "DONE"
            }
          }
        } else {
          for (c <- 1 to connsTotal) {
            ask(AkkaSupervisor.supervisor, Message.JOB(connsTotal, c, iter-1, isLastConnectionInPreviousIteration && c == connsTotal))(Timeout(20 minutes)).mapTo[String] onComplete
            {
              case Success("DONE") => _sender ! "DONE"
            }
          }
        }
      }
  }
}
