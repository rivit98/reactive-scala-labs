package EShop.lab5

import EShop.lab5.ProductCatalog.GetItems
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import spray.json.{DefaultJsonProtocol, JsString, JsValue, JsonFormat}

import java.net.URI
import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{Await, Future}


trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val uriFormat = new JsonFormat[java.net.URI] {
    override def write(obj: java.net.URI): spray.json.JsValue = JsString(obj.toString)
    override def read(json: JsValue): URI =
    json match {
      case JsString(url) => new URI(url)
      case _             => throw new RuntimeException("Parsing exception")
    }
  }

  implicit val itemFormat = jsonFormat5(ProductCatalog.Item)
  implicit val itemsFormat = jsonFormat1(ProductCatalog.Items)
}

class ProductCatalogAkkaHttpServer extends JsonSupport {
  private val config            = ConfigFactory.load()
  implicit val timeout: Timeout = 5.second
  implicit val system = ActorSystem[Nothing](
    Behaviors.empty,
    "ProductCatalog",
    config.getConfig("restapi").withFallback(config)
  )

  def routes: Route = {
    path("products") {
      get {
        parameter("brand".as[String], "keywords".as[String].repeated) { (brand, keywords) =>
          onSuccess(getItems(brand, keywords.toList)) {
            case ProductCatalog.Items(items) =>
              complete(items)
          }
        }
      }
    }
  }

  def getItems(brand: String, keywords: List[String]): Future[ProductCatalog.Items] = {
    val productCatalog            = ActorSystem[Nothing](Behaviors.empty, "ProductCatalog")
    implicit val scheduler        = productCatalog.scheduler
    implicit val executionContext = productCatalog.executionContext

    val listingFuture = system.receptionist.ask((ref: ActorRef[Receptionist.Listing]) =>
      Receptionist.find(ProductCatalog.ProductCatalogServiceKey, ref)
    )

    for {
      ProductCatalog.ProductCatalogServiceKey.Listing(listing) <- listingFuture
      productCatalog = listing.head
      items <- productCatalog.ask(ref => GetItems(brand, keywords, ref)).mapTo[ProductCatalog.Items]
    } yield items
  }

  def start(port: Int) = {
    val bindingFuture = Http().newServerAt("localhost", port).bind(routes)
    Await.ready(system.whenTerminated, Duration.Inf)
  }
}

object ProductCatalogAkkaHttpServerApp extends App {
  new ProductCatalogAkkaHttpServer().start(9000)
}
