package template.rest

import java.util.Date
import javax.ws.rs.client.ClientBuilder
import javax.ws.rs.core.{Context, MediaType, Response, UriInfo}
import javax.ws.rs.{GET, Path, Produces, QueryParam}

@Path("sync")
class Sync {
  @GET
  @Produces(Array("text/plain"))
  def sync(@Context uriInfo: UriInfo,
           @QueryParam("conns") conns: Int,
           @QueryParam("iter") iter: Int,
           @QueryParam("isInternalCall") isInternalCall: Boolean = false): Response = {
    val iterLeft = iter - 1
    if (! isInternalCall) {
      Stats.start
    }
    if (iterLeft >= 0) {
      for (i <- 1 to conns) {
        Stats.requesting
        Thread.sleep(Configs.bodyDelayMillis)
        val status: String = syncCall(uriInfo.getBaseUri().toString(), conns, iterLeft)
        Stats.replied
      }
    } else {
      Thread.sleep(Configs.leafDelayMillis);
    }
    if (! isInternalCall) {
      Stats.done
    }
    return Response.status(200).entity("OK %s".format(Stats.getDumpageStr())).build
  }

  def syncCall(baseUri: String, conns: Int, iter: Int): String = {
    val client = ClientBuilder.newClient()
    val response = client.target(baseUri)
      .path("sync")
      .queryParam("conns", conns.toString)
      .queryParam("iter", iter.toString)
      .queryParam("isInternalCall", "true")
      .request()
      .accept(MediaType.TEXT_PLAIN)
      .get()
    var result: String = null
    if (response.getStatus() != 200) {
      result = response.getStatus().toString
    } else {
      result = response.readEntity(classOf[String])
    }
    response.close()
    client.close()
    return result
  }
}
