package EShop.lab6

import EShop.lab5.{ProductCatalog, SearchService}
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.config.ConfigFactory

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.Try

object NodeProductCatalogAkkaHttpServer extends App {
  private val config      = ConfigFactory.load()
  private val workerCount = 3

  val system = ActorSystem[Nothing](
    Behaviors.setup[Nothing] { ctx =>
      for (i <- 1 to workerCount) yield ctx.spawn(ProductCatalog(new SearchService()), s"product-catalog-$i")
      Behaviors.same
    },
    "ProductCatalogCluster",
    config
      .getConfig(Try(args(0)).getOrElse(""))
      .withFallback(config)
  )

  Await.ready(system.whenTerminated, Duration.Inf)
}
