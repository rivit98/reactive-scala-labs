package EShop.lab6

import ch.qos.logback.classic.{Level, LoggerContext}
import io.gatling.app.Gatling
import io.gatling.core.Predef.{Simulation, StringBody, jsonFile, rampUsers, scenario, _}
import io.gatling.core.config.GatlingPropertiesBuilder
import io.gatling.http.Predef.http
import org.slf4j.LoggerFactory

import java.nio.file.Paths
import scala.concurrent.duration._

class ClusterProductCatalogGatlingTest extends Simulation {
  val context: LoggerContext = LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]
  context.getLogger("io.gatling.http").setLevel(Level.valueOf("WARN"))
  context.getLogger("io.gatling.core").setLevel(Level.valueOf("WARN"))

  val httpProtocol = http  //values here are adjusted to cluster_demo.sh script
    .baseUrls("http://localhost:9001", "http://localhost:9002", "http://localhost:9003")
    .acceptHeader("text/plain,text/html,application/json,application/xml;")
    .userAgentHeader("Mozilla/5.0 (Windows NT 5.1; rv:31.0) Gecko/20100101 Firefox/31.0")

  val scn = scenario("BasicSimulation")
    .feed(jsonFile(Paths.get(classOf[ProductCatalogGatlingTest].getResource("/data/work_data.json").toURI).toString).random)
    .exec(
      http("work_basic")
        .get("/products")
        .queryParam("brand", "${brandId}")
    )
    .pause(2.second, 4.seconds)

  setUp(
    scn.inject(
      incrementConcurrentUsers(100)
        .times(15)
        .eachLevelLasting(20.seconds)
        .separatedByRampsLasting(5.seconds)
        .startingFrom(50)
    )
  ).protocols(httpProtocol)
}

object ClusterTest {
  def main(args: Array[String]): Unit ={
    val props = new GatlingPropertiesBuilder
    props.simulationClass("EShop.lab6.ClusterProductCatalogGatlingTest")
    Gatling.fromMap(props.build)
  }
}