package template.rest

import java.util.Date
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.ws.rs.client.InvocationCallback
import javax.ws.rs.container.{AsyncResponse, Suspended}
import javax.ws.rs.core.{Context, MediaType, Response, UriInfo}
import javax.ws.rs.{GET, Path, Produces, QueryParam}

@Path("async")
class Async {
  @GET
  @Produces(Array("text/plain"))
  def async(@Context uriInfo: UriInfo,
            @QueryParam("conns") conns: Int,
            @QueryParam("iter") iter: Int,
            @QueryParam("isInternalCall") isInternalCall: Boolean = false,
            @Suspended response: AsyncResponse) = {
    val iterLeft = iter - 1
    val baseUri = uriInfo.getBaseUri().toString()
    var connsLeft: AtomicInteger = new AtomicInteger(conns)
    response.setTimeout(20l,TimeUnit.MINUTES)
    if (! isInternalCall) {
      Stats.start
    }

    val fn = () =>
      if (iterLeft >= 0) {
        for (i <- 1 to conns) {
          Stats.requesting
          var callback = new JaxRsAsyncCallback(connsLeft, response, ! isInternalCall)
          Thread.sleep(Configs.bodyDelayMillis)
          jaxrsAsyncCall(baseUri, conns, iterLeft, callback)
        }
      } else {
        Thread.sleep(Configs.leafDelayMillis);
        response.resume(Response.status(200).entity("OK %s".format(Stats.getDumpageStr())).build)
      }

    if (Configs.useThreadForAsync) {
      (new Thread(new Runnable {@Override def run() {fn()}})).start()
    } else {
      fn()
    }
  }

  class JaxRsAsyncCallback(connsLeft: AtomicInteger,
                           response: AsyncResponse,
                           isTop: Boolean)
    extends InvocationCallback[Response]() {

    @Override def completed(reply: Response) {
      val result: String = reply.readEntity(classOf[String])
      reply.close()
      Stats.replied
      if (connsLeft.decrementAndGet() == 0) {
        response.resume(Response.status(200).entity("OK %s".format(Stats.getDumpageStr())).build)
        if (isTop) {
          Stats.done
        }
      }
    }

    @Override def failed(throwable: Throwable): Unit = {
      Stats.errored
      if (connsLeft.decrementAndGet() == 0) {
        response.resume(Response.status(500).entity("error").build)
        if (isTop) {
          Stats.done
        }
      }
    }
  }

  def jaxrsAsyncCall(baseUri: String, conns: Int, iter: Int, callback: InvocationCallback[Response]) = {
    AsyncClient.client.target(baseUri)
    .path("async")
    .queryParam("conns", conns.toString)
    .queryParam("iter", iter.toString)
    .queryParam("isInternalCall", "true")
    .request()
    .accept(MediaType.TEXT_PLAIN)
    .async()
    .get(callback)
  }
}
