package EShop.lab6

import EShop.lab5.ProductCatalog.{GetItems, ProductCatalogServiceKey}
import EShop.lab5.{JsonSupport, ProductCatalog}
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.{Behaviors, Routers}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.typesafe.config.ConfigFactory

import scala.concurrent.Await
import scala.concurrent.duration.{Duration, DurationInt}
import scala.util.Try

class ProductCatalogAkkaHttpServer extends JsonSupport {
  private val config            = ConfigFactory.load()
  implicit val timeout: Timeout = 5.second
  implicit val system = ActorSystem[Nothing](
    Behaviors.empty,
    "ProductCatalogCluster",
    config
  )

  val workers                   = system.systemActorOf(Routers.group(ProductCatalogServiceKey), "ProductCatalogCluster")
  implicit val scheduler        = system.scheduler
  implicit val executionContext = system.executionContext

  def routes: Route = {
    path("products") {
      get {
        parameter("brand".as[String], "keywords".as[String].repeated) { (brand, keywords) =>
//          println(s"Work to be distributed - $brand")
          val items = workers.ask(replyTo => GetItems(brand, keywords.toList, replyTo)).mapTo[ProductCatalog.Items]
          onSuccess(items) {
            case ProductCatalog.Items(items) =>
              complete(items)
          }
        }
      }
    }
  }

  def start(port: Int): Unit = {
    val bindingFuture = Http().newServerAt("localhost", port).bind(routes)
    Await.ready(system.whenTerminated, Duration.Inf)
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }
}

object WorkHttpClusterNodeApp extends App {
  new ProductCatalogAkkaHttpServer().start(Try(args(0).toInt).getOrElse(9123))
}
