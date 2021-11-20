package EShop.lab6

import EShop.lab6.StatsNodeActor.GetStats
import akka.actor.typed.pubsub.Topic
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.typesafe.config.ConfigFactory

import scala.concurrent.Await
import scala.concurrent.duration.{Duration, DurationInt}
import scala.util.Try

object StatsNodeActor {
  sealed trait Message
  case object SearchCompleted                 extends Message
  case class GetStats(replyTo: ActorRef[Any]) extends Message

  case class Stats(nrOfRequests: Int)

  private var numberOfRequests = 0

  def apply(): Behavior[Message] =
    Behaviors.setup { context =>
      val topic = context.spawn(Topic[Message]("stats-topic"), "StatsTopic")
      topic ! Topic.Subscribe(context.self)

      Behaviors.receiveMessage {
        case SearchCompleted =>
          numberOfRequests += 1
          Behaviors.same
        case GetStats(replyTo) =>
          replyTo ! numberOfRequests
          Behaviors.same
      }
    }
}

class StatsNodeHttpServer {
  private val config            = ConfigFactory.load()
  implicit val timeout: Timeout = 5.second
  implicit val system = ActorSystem[Nothing](
    Behaviors.empty,
    "ProductCatalogCluster",
    config
      .getConfig("stats-node")
      .withFallback(config)
  )

  val statsNode: ActorRef[StatsNodeActor.Message] = system.systemActorOf(StatsNodeActor(), "StatsNode")
  implicit val scheduler                          = system.scheduler
  implicit val executionContext                   = system.executionContext

  def routes: Route = {
    path("stats") {
      get {
        val stats = statsNode.ask(replyTo => GetStats(replyTo)).mapTo[Int]
        onSuccess(stats) { n =>
          complete(n.toString)
        }
      }
    }
  }

  def start(port: Int): Unit = {
    val bindingFuture = Http().newServerAt("localhost", port).bind(routes)
    Await.ready(system.whenTerminated, Duration.Inf)
    bindingFuture
      .flatMap(_.unbind())                 // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }
}

object StatsNode extends App {
  new StatsNodeHttpServer().start(Try(args(0).toInt).getOrElse(9888))
}
