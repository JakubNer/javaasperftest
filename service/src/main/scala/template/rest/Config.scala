package template.rest

import javax.ws.rs.core.Response
import javax.ws.rs.{POST, Path, QueryParam}

@Path("config")
class Config {
  @POST
  def config(@QueryParam("bodyDelayMillis") bodyDelayMillis: Int,
             @QueryParam("leafDelayMillis") leafDelayMillis: Int,
             @QueryParam("numAsyncRequests") numAsyncRequests: Int,
             @QueryParam("numWorkers") numWorkers: Int,
             @QueryParam("useThreadForAsync") useThreadForAsync: Boolean): Response = {
    Configs.bodyDelayMillis = bodyDelayMillis
    Configs.leafDelayMillis = leafDelayMillis
    Configs.numAsyncRequests = numAsyncRequests
    Configs.numWorkers = numWorkers
    Configs.useThreadForAsync = useThreadForAsync

    AsyncClient.reopen(numAsyncRequests)

    return Response.status(200).build
  }
}
