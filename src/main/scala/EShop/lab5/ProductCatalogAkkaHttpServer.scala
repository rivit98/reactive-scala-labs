package EShop.lab5

import EShop.lab5.ProductCatalog.GetItems
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.{Behaviors, Routers}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import spray.json.{DefaultJsonProtocol, JsString, JsValue, JsonFormat}

import java.net.URI
import scala.concurrent.Await
import scala.concurrent.duration.{Duration, DurationInt}
import scala.util.Try

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val uriFormat = new JsonFormat[java.net.URI] {
    override def write(obj: java.net.URI): spray.json.JsValue = JsString(obj.toString)
    override def read(json: JsValue): URI =
      json match {
        case JsString(url) => new URI(url)
        case _             => throw new RuntimeException("Parsing exception")
      }
  }

  implicit val itemFormat  = jsonFormat5(ProductCatalog.Item)
  implicit val itemsFormat = jsonFormat1(ProductCatalog.Items)
}

class ProductCatalogAkkaHttpServer extends JsonSupport {
  private val config            = ConfigFactory.load()
  implicit val timeout: Timeout = 5.second
  implicit val system = ActorSystem[Nothing](
    Behaviors.empty,
    "ProductCatalog",
    config
  )

  val workers                   = system.systemActorOf(Routers.pool(3)(ProductCatalog(new SearchService())), "workersRouter")
  implicit val scheduler        = system.scheduler
  implicit val executionContext = system.executionContext

  def routes: Route = {
    path("products") {
      get {
        parameter("brand".as[String], "keywords".as[String].repeated) { (brand, keywords) =>
          val items = workers.ask(replyTo => GetItems(brand, keywords.toList, replyTo)).mapTo[ProductCatalog.Items]
          onSuccess(items) {
            case ProductCatalog.Items(items) =>
              complete(items)
          }
        }
      }
    }
  }

  def start(port: Int) = {
    val bindingFuture = Http().newServerAt("localhost", port).bind(routes)
    Await.ready(system.whenTerminated, Duration.Inf)
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }
}

object ProductCatalogAkkaHttpServerApp extends App {
  new ProductCatalogAkkaHttpServer().start(Try(args(0).toInt).getOrElse(9123))
}
