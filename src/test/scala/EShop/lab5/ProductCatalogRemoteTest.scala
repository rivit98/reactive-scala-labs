package EShop.lab5

import EShop.lab5.ProductCatalog.GetItems
import EShop.lab6.ProductCatalogAkkaHttpServer
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import org.scalatest.flatspec.AsyncFlatSpecLike
import org.scalatest.matchers.should.Matchers

import java.util.concurrent.Executors
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

class ProductCatalogRemoteTest extends AsyncFlatSpecLike with Matchers {

  implicit val timeout: Timeout = 3.second

  "A remote Product Catalog" should "return search results" in {
    val server = new ProductCatalogAkkaHttpServer()

    Future {
      server.start(9000)
    }(ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor()))

    val config = ConfigFactory.load()

    val actorSystem =
      ActorSystem[Nothing](
        Behaviors.empty,
        "ProductCatalogCluster",
        config.getConfig("productcatalog").withFallback(config)
      )
    actorSystem.systemActorOf(ProductCatalog(new SearchService()), "productcatalog")

    val anotherActorSystem =
      ActorSystem[Nothing](Behaviors.empty, "ProductCatalogCluster")
    implicit val scheduler = anotherActorSystem.scheduler

    // wait for the cluster to form up
    Thread.sleep(10000)

    val listingFuture = anotherActorSystem.receptionist.ask(
      (ref: ActorRef[Receptionist.Listing]) => Receptionist.find(ProductCatalog.ProductCatalogServiceKey, ref)
    )

    for {
      ProductCatalog.ProductCatalogServiceKey.Listing(listing) <- listingFuture
      productCatalog = listing.head
      items <- productCatalog.ask(ref => GetItems("gerber", List("cream"), ref)).mapTo[ProductCatalog.Items]
      _ = actorSystem.terminate()
      _ = anotherActorSystem.terminate()
      _ = server.system.terminate()
    } yield {
      assert(items.items.size == 10)
    }
  }
}
