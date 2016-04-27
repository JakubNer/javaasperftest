package template.rest

import java.util.Date
import java.util.concurrent.atomic.AtomicInteger
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder

object AsyncClient {
  var client = (new ResteasyClientBuilder())
    .connectionPoolSize(Configs.numAsyncRequests)
    .build()

  def reopen(numAsyncRequests : Int) = {
    println("repoening client w/%d requests".format(numAsyncRequests))
    client.close()
    client = (new ResteasyClientBuilder())
      .connectionPoolSize(numAsyncRequests)
      .build()
  }
}

object Configs {
  var bodyDelayMillis: Int = 0
  var leafDelayMillis: Int = 100
  var numAsyncRequests: Int = 3
  var numWorkers: Int = 3
  var useThreadForAsync: Boolean = false
}

object Stats {
  private var date: java.text.SimpleDateFormat = new java.text.SimpleDateFormat("HH:mm:ss:SSS")

  private var begin: String = null
  private var end: String = null
  private var requests: AtomicInteger = new AtomicInteger
  private var replies: AtomicInteger = new AtomicInteger
  private var errors: AtomicInteger = new AtomicInteger

  private class Reason(val str:String) {}

  private val R_START: Reason = new Reason("START")
  private val R_DONE: Reason = new Reason("DONE ")
  private var R_REQ: Reason = new Reason("REQ  ")
  private var R_RPL: Reason = new Reason("REPL ")
  private var R_ERR: Reason = new Reason("ERROR")

  def start = {
    begin = date.format((new Date))
    end = null
    requests = new AtomicInteger
    replies = new AtomicInteger
    errors = new AtomicInteger
    dump(R_START)
  }

  def getDumpageStr(reason:String = ""): String = {
    var doneStr : String = ""
    if (end != null) {
      doneStr = "(done:%s)".format(end)
    }
    return "|| %s || (start:%s)(req:%06d)(rpl:%06d)(err:%06d)%s".format(reason, begin, requests.get(), replies.get(), errors.get(), doneStr);
  }

  def dump() : String = {
    return getDumpageStr()
  }

  def dump(reason:Reason) = {
    println(getDumpageStr(reason.str))
  }

  def done = {
    end = date.format((new Date))
    dump(R_DONE)
  }

  def requesting = {
    requests.incrementAndGet()
    dump(R_REQ)
  }

  def replied = {
    replies.incrementAndGet()
    dump(R_RPL)
  }

  def errored = {
    replies.incrementAndGet()
    errors.incrementAndGet()
    dump(R_ERR)
  }
}

